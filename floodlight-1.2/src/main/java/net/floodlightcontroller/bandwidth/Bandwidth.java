package net.floodlightcontroller.bandwidth;
import java.util.Collection;
import java.util.ArrayList; 
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFQueueStatsEntry;
import org.projectfloodlight.openflow.protocol.OFQueueStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsRequest;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.match.Match;

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
import net.floodlightcontroller.frogqos.IFrogQosService;
import net.floodlightcontroller.core.IFloodlightProviderService; 
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.threadpool.IThreadPoolService;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.google.common.primitives.UnsignedLong;
import com.google.common.util.concurrent.ListenableFuture;


public class Bandwidth  implements IOFMessageListener,
		IFloodlightModule,IOFSwitchListener{
	
	private static final Map<Long, Double> speed_map = new HashMap<Long, Double>();
	private static final Map<Long, Long> last_map = new HashMap<Long, Long>();
	private static final int ENTITY_INTERVAL = 3;
	protected static final double DoubleCount = 3.0;
	private static double SPEED_FUDONG=0.1;
	private static double SPEED_EXTEND=1.05;
	private static double TOTAL_BANDWIDTH=10;
	private static final DatapathId swid = DatapathId.of("00:00:00:00:00:00:00:01");
	private IFrogQosService frogQosService;

	protected IThreadPoolService threadPool;
	protected IOFSwitchService switchService;
	protected static Logger logger;

	public SingletonTask entityTask;

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
		Iterator<Business_struct> it = gLink.iterator();
		double remain_band = TOTAL_BANDWIDTH;
		while(it.hasNext())
		{
			if(it.next().Prio<b.Prio){			
				gLink.add(gLink.indexOf(it)+1,b);
				flag_add = true;
				checkSpeed(it,remain_band);
				break;
			}
			remain_band -= it.next().Bandwidth_guara;
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
		Double get = speed_map.get(queueId);
		if(get==null){
			return -1;
		}
		else return get;
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
		l.add(IFrogQosService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub
		gLink = new LinkedList<Business_struct>();
		ungLink = new LinkedList<Business_struct>();
		frogQosService = context.getServiceImpl(IFrogQosService.class);
		this.threadPool = context.getServiceImpl(IThreadPoolService.class);
		this.switchService = context.getServiceImpl(IOFSwitchService.class);
		logger = LoggerFactory.getLogger(Bandwidth.class);
		logger = LoggerFactory.getLogger(SwitchDescription.class);
		Integer qid =  frogQosService.createQueue("10000000","2000000");
		speed_map.put(new Long(qid),new Double(0));
		qid =  frogQosService.createQueue("10000000","2000000");
		speed_map.put(new Long(qid),new Double(0));
	}

	
	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub
		ScheduledExecutorService ses = threadPool.getScheduledExecutor();
		Runnable ecr = new Runnable() {
			@Override
			public void run() {
				//cleanupEntities();
				task_getSpeed();
				entityTask.reschedule(ENTITY_INTERVAL,
						TimeUnit.SECONDS);
			}
		};
		entityTask = new SingletonTask(ses, ecr);
		entityTask.reschedule(ENTITY_INTERVAL,
				TimeUnit.SECONDS);
		logger.info("test logger info");
	}

	private void task_getSpeed()
	{
		IOFSwitch sw = switchService.getSwitch(swid);
		if(sw==null) return;
		StatsReply result = new StatsReply();
		Match match;
		ListenableFuture<?> future;
		List<OFStatsReply> values = null;
		OFStatsRequest<?> req = null;
		/*req = sw.getOFFactory().buildQueueStatsRequest()
				.setPortNo(OFPort.ANY)
				.setQueueId(UnsignedLong.MAX_VALUE.longValue())
				.build();*/
		req = sw.getOFFactory().buildQueueStatsRequest().setPortNo(OFPort.ANY).setQueueId(UnsignedLong.MAX_VALUE.longValue()).build();
		try {
			if (req != null) {
				future = sw.writeStatsRequest(req);
				//values = (List<OFStatsReply>)future.get();
				values = (List<OFStatsReply>) future.get(10, TimeUnit.SECONDS);

			}
		} catch (Exception e) {
			//logger.error("Failure retrieving statistics from switch {}. {}", sw, e);
		}
		Iterator<OFStatsReply> it = values.iterator();
		while(it.hasNext())
		{
			OFQueueStatsReply queueStatsReply = (OFQueueStatsReply) it.next();
			//logger.info(queueStatsReply.toString());
			List<OFQueueStatsEntry> entrylist = queueStatsReply.getEntries();
			logger.info("!!!!!!!!!! size: "+entrylist.size());
			if (entrylist.size()>0)
				logger.info("!");
			/*Iterator<OFQueueStatsEntry> entryiter = entrylist.iterator();
			long value = 0;
			double rate = 0;
			while (entryiter.hasNext()) {
				OFQueueStatsEntry entry = entryiter.next();
				Long id = entry.getQueueId();
				value = entry.getTxBytes().getValue() - last_map.get(id);
				last_map.put(id,entry.getTxBytes().getValue());
				rate = value/DoubleCount;
				speed_map.put(id,rate);
				logger.info("getspeed:"+id+"  "+rate);
			}*/
		}
	}

	@Override
	public Command receive(IOFSwitch sw, OFMessage msg,
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
