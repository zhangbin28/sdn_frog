package net.floodlightcontroller.bandwidth;
import java.awt.BorderLayout;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.ArrayList; 
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFQueueStatsEntry;
import org.projectfloodlight.openflow.protocol.OFQueueStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsRequest;
import org.projectfloodlight.openflow.protocol.OFStatsType;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionEnqueue;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.ver13.OFMeterSerializerVer13;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.web.StatsReply;
import net.floodlightcontroller.core.IFloodlightProviderService; 
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.packet.Ethernet; 
import net.floodlightcontroller.util.OFMessageDamper;
import net.floodlightcontroller.threadpool.IThreadPoolService;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.TransportPort;
import org.slf4j.Logger; 
import org.slf4j.LoggerFactory;

import com.google.common.primitives.UnsignedLong;
import com.google.common.util.concurrent.ListenableFuture;

public class Bandwidth  implements IOFMessageListener,
		IFloodlightModule,IOFSwitchListener{
	private static double SPEED_FUDONG=0.1;
	private static double SPEED_EXTEND=1.05;
	private static double TOTAL_BANDWIDTH=10;
	public class Business_struct{
		String IP;
		int Port;
		int Prio;
		int QueueId;
		double Bandwidth_guara;
		double Bandwidth_last;
		boolean isunLink;
		String flowId;
		Business_struct(){
			IP="0";Port=0;Prio=0;
			QueueId=0;Bandwidth_guara=0;Bandwidth_last=0;
			flowId="0";isunLink=true;
		}
	}
	private LinkedList<Business_struct> ungLink,gLink;
	private void insertGlink(Business_struct b)
	{
		boolean flag_add = false;
		for(int i=0;i<gLink.size();i++){
			if(gLink.get(i).Prio<b.Prio){
				gLink.add(i,b);
				flag_add = true;
				break;
			}
		}
		Iterator<Business_struct> it = gLink.iterator();
		double remain_band = TOTAL_BANDWIDTH;
		while(it.hasNext())
		{
			remain_band -= it.next().Bandwidth_guara;
			if(it.next().Prio<b.Prio){			
				gLink.add(gLink.indexOf(it)+1,b);
				flag_add = true;
				checkSpeed(it,remain_band);
				break;
			}
		}				
		if(!flag_add)
		{
			gLink.addLast(b);
			checkSpeed(it,remain_band);
		}
		
	}
	private void insertUglink(Business_struct b)
	{
		ungLink.add(b);
		
	}
	 	
	private void checkSpeed(Iterator<Business_struct> it,double remain_bandwidth)
	{
		Business_struct t;
		while(it.hasNext())
		{
			t = it.next();
			if(remain_bandwidth==0)
			{
				if(!t.isunLink)
				{
					changeTounQueue(t.QueueId,t.flowId);
					t.isunLink=true;
				}
				continue;
			}
			else{
				if(t.isunLink){
					t.isunLink = false;
					if(remain_bandwidth>=1){
						t.QueueId = createQueue(1,TOTAL_BANDWIDTH);
						remain_bandwidth-=1;
						}
					else {
						t.QueueId = createQueue(remain_bandwidth,TOTAL_BANDWIDTH);
						remain_bandwidth=0;
						}
					continue;
				}
			}
			double curSpeed=getSpeed(t.QueueId);
			double curChange=(curSpeed-t.Bandwidth_last)/t.Bandwidth_last;
			if(curChange>SPEED_FUDONG){
				if(curSpeed*SPEED_EXTEND<remain_bandwidth)
				{
					changeQueue(t.QueueId,curSpeed*SPEED_EXTEND,TOTAL_BANDWIDTH);
					remain_bandwidth-=curSpeed*SPEED_EXTEND;
				}
				else{
					changeQueue(t.QueueId,remain_bandwidth,TOTAL_BANDWIDTH);
					remain_bandwidth=0;
				}
			}
			else if(curChange<(-1*SPEED_FUDONG)){
				changeQueue(t.QueueId,curSpeed*SPEED_EXTEND,TOTAL_BANDWIDTH);
				remain_bandwidth-=curSpeed*SPEED_EXTEND;
			}
			else ;			
		}			
	}
	private void changeTounQueue(int queueId,String flowId)
	{
		
	}
	private int createQueue(double min_speed,double max_speed)   // return queueid
	{
		return 0;
	}
	private void changeQueue(int queueId,double min_speed,double max_speed)
	{
		
	}
	private double getSpeed(int queueId)
	{
		return 0;
	}
	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return Bandwidth.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}


	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		// TODO Auto-generated method stub
		Collection<Class<? extends IFloodlightService>> l = 
				new ArrayList<Class<? extends IFloodlightService>>(); 
		l.add(IFloodlightProviderService.class); 
		l.add(IOFSwitchService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub
		gLink = new LinkedList<Business_struct>();
		ungLink = new LinkedList<Business_struct>();
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(IOFSwitch sw, OFMessage msg,
			FloodlightContext cntx) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void switchAdded(DatapathId switchId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void switchRemoved(DatapathId switchId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void switchActivated(DatapathId switchId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void switchPortChanged(DatapathId switchId, OFPortDesc port, PortChangeType type) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void switchChanged(DatapathId switchId) {
		// TODO Auto-generated method stub
		
	}
	
}
