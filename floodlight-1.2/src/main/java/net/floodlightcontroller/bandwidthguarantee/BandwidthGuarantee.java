package net.floodlightcontroller.bandwidthguarantee;

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

public class BandwidthGuarantee extends JFrame implements IOFMessageListener,
		IFloodlightModule,IOFSwitchListener {
	
	private static final long serialVersionUID = 1L;
	protected IFloodlightProviderService floodlightProvider; 
	protected static Logger logger;
	protected IOFSwitchService switchService;
	protected IOFSwitch mySwitch;
	public static int FLOWMOD_DEFAULT_IDLE_TIMEOUT = 5; // in seconds
	public static int FLOWMOD_DEFAULT_HARD_TIMEOUT = 30; // infinite
	public static int FLOWMOD_DEFAULT_PRIORITY = 2; // 0 is the default table-miss flow in OF1.3+, so we need to use 1
	public static final int FORWARDING_APP_ID = 2; // TODO: This must be managed
	protected OFMessageDamper messageDamper;

	private static long oldQueueTx[] = new long[10];
	private static double QueueSpeed[] = new double[10];
	private static boolean resolver = true; 
	private static boolean flowclock = true;
	
	private static int count = 0;
	private static double chart[][] = new double[20][2];
	private static double videosp = 0;
	private static double ftpsp = 0;
	
	private OFPacketIn piqos;
	private FloodlightContext cntxqos;
	
	private static File fin;
	private static OutputStream out;
	private static String filenameTemp = "MyData.txt"; 
	
	protected IThreadPoolService threadPool;
	public Map<SwitchPort, Double> rateMap = null;
	
	protected static final int ENTITY_INTERVAL = 3;
	protected static final double DoubleCount = 3.0;
	public SingletonTask entityTask;
	public SingletonTask entityTaskChart;
	//定义表格  
    public JTable table;  
    //定义滚动条面板(用以使表格可以滚动)  
    public JScrollPane scrollPane;  
    //定义数据模型类的对象(用以保存数据)，  
    public DefaultTableModel tableModel;  
	
	class SwitchPort{
		private OFPort port;
		private int queue;
		public SwitchPort() {
			// TODO Auto-generated constructor stub
			port = null;
			queue = 0;
		}
		OFPort getPort(){
			return port;
		}
		int getQueue(){
			return queue;
		}
		void setPort(OFPort pno){
			port = pno;
		}
		void setQueue(int qid){
			queue = qid;
		}
	}
	
	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return BandwidthGuarantee.class.getSimpleName();
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
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// TODO Auto-generated method stub
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class); 
		logger = LoggerFactory.getLogger(BandwidthGuarantee.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
		logger = LoggerFactory.getLogger(SwitchDescription.class);
		this.threadPool = context.getServiceImpl(IThreadPoolService.class);
		rateMap = new HashMap<SwitchPort, Double>();

	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// TODO Auto-generated method stub
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);		
		switchService.addOFSwitchListener(this);
		DataChart();
		fin = new File("MyXml.xml");
		File f = new File("MyData.txt");
		FileWriter fw;
		try {
			fw = new FileWriter(f);
			fw.write("");
			fw.close();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		try{
			out = new FileOutputStream("MyXml.xml");
		}
		catch(Exception e){
			System.out.println("Fail to new the OutputStream");
		}

		ScheduledExecutorService ses = threadPool.getScheduledExecutor();
		Runnable ecr = new Runnable() {
			@Override
			public void run() {
				//cleanupEntities();
				SendPortStaticsRequest(DatapathId.of("00:00:00:00:00:00:00:01"));
				entityTask.reschedule(ENTITY_INTERVAL,
						TimeUnit.SECONDS);
			}
		};
		entityTask = new SingletonTask(ses, ecr);
		entityTask.reschedule(ENTITY_INTERVAL,
				TimeUnit.SECONDS);
		ScheduledExecutorService seschart = threadPool.getScheduledExecutor();
		Runnable ecrchart = new Runnable() {
			@Override
			public void run() {
				//cleanupEntities();
				Refresh();
				entityTaskChart.reschedule(ENTITY_INTERVAL,
						TimeUnit.SECONDS);
			}
		};
		entityTaskChart = new SingletonTask(seschart, ecrchart);
		entityTaskChart.reschedule(ENTITY_INTERVAL,
				TimeUnit.SECONDS);
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		// TODO Auto-generated method stub
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD); 
		//logger.info("This is Neo");		
		// We only care about packet-in messages
		if (msg.getType() != OFType.PACKET_IN) { 
			// Allow the next module to also process this OpenFlow message
			//logger.info("This is not packetin message");
			return Command.CONTINUE;
		}
		
		OFPacketIn pi = (OFPacketIn)msg;
		
		
		if (eth.getEtherType().getValue() == Ethernet.TYPE_IPv4) {
			
			//logger.info("This is packetin message");
			if(sw.getId().equals(DatapathId.of(1))){
				mySwitch = sw;
				logger.info("The Switch is {} ",mySwitch.getId());
			}
			
			if(sw.getId().equals(DatapathId.of(1))){	
				piqos = pi;
				cntxqos = cntx;
				//logger.info("Get the right IP packet {}",swdp);
				if(QueueSpeed[4]>=80000)
					resolver = false;
				else if(QueueSpeed[1]<=10000)
					resolver = true;
				if(resolver){
					VideoRegularFlow(mySwitch, pi, cntx);
					FtpRegularFlow(mySwitch, pi, cntx);
				}
				else{
					VideoQoSFlow(mySwitch, pi, cntx);
					FtpQoSFlow(mySwitch, pi, cntx);
					//VideoRegularFlow(mySwitch, piqos, cntxqos);
					//FtpRegularFlow(mySwitch, piqos, cntxqos);
				}			
			}
		}		
		return Command.CONTINUE;
	}
	
	private void VideoQoSFlow(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
		
		Match myMatch = sw.getOFFactory().buildMatch() 
				.setExact(MatchField.IN_PORT, OFPort.of(2)) 
				.setExact(MatchField.ETH_TYPE, EthType.IPv4) 
				.setExact(MatchField.IPV4_SRC, IPv4Address.of("10.0.0.2")) 
				.setExact(MatchField.IPV4_DST, IPv4Address.of("10.0.0.1"))
				.setExact(MatchField.IP_PROTO, IpProtocol.TCP) 
				.setExact(MatchField.TCP_SRC, TransportPort.of(8800)) 
				.build();
		ArrayList<OFAction> actionList = new ArrayList<OFAction>(); 
		OFActions actions = sw.getOFFactory().actions();
		OFActionEnqueue enqueue = actions.buildEnqueue()
				.setPort(OFPort.of(1))
				.setQueueId(1)
				.build();
		actionList.add(enqueue);
		OFFlowAdd flowAdd = sw.getOFFactory().buildFlowAdd() 
				.setBufferId(OFBufferId.NO_BUFFER) 
				.setHardTimeout(3600) 
				.setIdleTimeout(60) 
				.setPriority(32768) 
				.setMatch(myMatch)
				.setActions(actionList)
				.build();		
		sw.write(flowAdd);
		System.out.println("Push out the Video QoS Flow.");		
	}
	
	private void FtpQoSFlow(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
		
		Match myMatch = sw.getOFFactory().buildMatch() 
				.setExact(MatchField.IN_PORT, OFPort.of(3)) 
				.setExact(MatchField.ETH_TYPE, EthType.IPv4) 
				.setExact(MatchField.IPV4_SRC, IPv4Address.of("10.0.0.3")) 
				.setExact(MatchField.IPV4_DST, IPv4Address.of("10.0.0.1"))
				.setExact(MatchField.IP_PROTO, IpProtocol.TCP) 
				.setExact(MatchField.TCP_SRC, TransportPort.of(20)) 
				.build();
		ArrayList<OFAction> actionList = new ArrayList<OFAction>(); 
		OFActions actions = sw.getOFFactory().actions();
		OFActionEnqueue enqueue = actions.buildEnqueue()
				.setPort(OFPort.of(1))
				.setQueueId(2)
				.build();
		actionList.add(enqueue);
		OFFlowAdd flowAdd = sw.getOFFactory().buildFlowAdd() 
				.setBufferId(OFBufferId.NO_BUFFER) 
				.setHardTimeout(3600) 
				.setIdleTimeout(60) 
				.setPriority(32768) 
				.setMatch(myMatch)
				.setActions(actionList)
				.build();		
		sw.write(flowAdd);
		System.out.println("Push out the FTP QoS Flow.");		
	}
	
	private void VideoRegularFlow(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
		
		Match myMatch = sw.getOFFactory().buildMatch() 
				.setExact(MatchField.IN_PORT, OFPort.of(2)) 
				.setExact(MatchField.ETH_TYPE, EthType.IPv4) 
				.setExact(MatchField.IPV4_SRC, IPv4Address.of("10.0.0.2")) 
				.setExact(MatchField.IPV4_DST, IPv4Address.of("10.0.0.1"))
				.setExact(MatchField.IP_PROTO, IpProtocol.TCP) 
				.setExact(MatchField.TCP_SRC, TransportPort.of(8800)) 
				.build();
		ArrayList<OFAction> actionList = new ArrayList<OFAction>(); 
		OFActions actions = sw.getOFFactory().actions();
		OFActionEnqueue enqueue = actions.buildEnqueue()
				.setPort(OFPort.of(1))
				.setQueueId(3)
				.build();
		actionList.add(enqueue);
		OFFlowAdd flowAdd = sw.getOFFactory().buildFlowAdd() 
				.setBufferId(OFBufferId.NO_BUFFER) 
				.setHardTimeout(3600) 
				.setIdleTimeout(60) 
				.setPriority(32768) 
				.setMatch(myMatch)
				.setActions(actionList) 
				.build();
		sw.write(flowAdd);
		System.out.println("Push out the Video Regular Flow.");		
	}
	
	private void FtpRegularFlow(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
		
		Match myMatch = sw.getOFFactory().buildMatch() 
				.setExact(MatchField.IN_PORT, OFPort.of(3)) 
				.setExact(MatchField.ETH_TYPE, EthType.IPv4) 
				.setExact(MatchField.IPV4_SRC, IPv4Address.of("10.0.0.3")) 
				.setExact(MatchField.IPV4_DST, IPv4Address.of("10.0.0.1"))
				.setExact(MatchField.IP_PROTO, IpProtocol.TCP) 
				.setExact(MatchField.TCP_SRC, TransportPort.of(20)) 
				.build();
		ArrayList<OFAction> actionList = new ArrayList<OFAction>(); 
		OFActions actions = sw.getOFFactory().actions();
		OFActionEnqueue enqueue = actions.buildEnqueue()
				.setPort(OFPort.of(1))
				.setQueueId(4)
				.build();
		actionList.add(enqueue);
		OFFlowAdd flowAdd = sw.getOFFactory().buildFlowAdd() 
				.setBufferId(OFBufferId.NO_BUFFER) 
				.setHardTimeout(3600) 
				.setIdleTimeout(60) 
				.setPriority(32768) 
				.setMatch(myMatch)
				.setActions(actionList)
				.build();		
		sw.write(flowAdd);
		System.out.println("Push out the FTP Regular Flow.");		
	}
	
	/**
	 * Pushes a packet-out to a switch.  The assumption here is that
	 * the packet-in was also generated from the same switch.  Thus, if the input
	 * port of the packet-in and the outport are the same, the function will not
	 * push the packet-out.
	 * @param sw        switch that generated the packet-in, and from which packet-out is sent
	 * @param pi        packet-in
	 * @param useBufferId  if true, use the bufferId from the packet in and
	 * do not add the packetIn's payload. If false set bufferId to
	 * BUFFER_ID_NONE and use the packetIn's payload
	 * @param outport   output port
	 * @param cntx      context of the packet
	 */
	protected void pushPacket(IOFSwitch sw, OFPacketIn pi, boolean useBufferId,
			OFPort outport, FloodlightContext cntx) {

		if (pi == null) {
			return;
		}

		// The assumption here is (sw) is the switch that generated the
		// packet-in. If the input port is the same as output port, then
		// the packet-out should be ignored.
		if ((pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT)).equals(outport)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Attempting to do packet-out to the same " +
						"interface as packet-in. Dropping packet. " +
						" SrcSwitch={}, pi={}",
						new Object[]{sw, pi});
				return;
			}
		}

		if (logger.isTraceEnabled()) {
			logger.trace("PacketOut srcSwitch={} pi={}",
					new Object[] {sw, pi});
		}

		OFPacketOut.Builder pob = sw.getOFFactory().buildPacketOut();
		// set actions
		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(sw.getOFFactory().actions().output(outport, Integer.MAX_VALUE));
		pob.setActions(actions);

		if (useBufferId) {
			pob.setBufferId(pi.getBufferId());
		} else {
			pob.setBufferId(OFBufferId.NO_BUFFER);
		}

		if (pob.getBufferId() == OFBufferId.NO_BUFFER) {
			byte[] packetData = pi.getData();
			pob.setData(packetData);
		}

		pob.setInPort((pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT)));

		try {
			messageDamper.write(sw, pob.build());
		} catch (IOException e) {
			logger.error("Failure writing packet out", e);
		}
	}

	@SuppressWarnings("unchecked")
	protected StatsReply SendStaticsRequset(DatapathId switchId,OFStatsType statsType){
		IOFSwitch sw = switchService.getSwitch(switchId);
		ListenableFuture<?> future;
		List<OFStatsReply> values = null;
		StatsReply result = new StatsReply();
		Match match;
		if (sw != null) {
			
			OFStatsRequest<?> req = null;
			switch (statsType) {		
			case FLOW:
				match = sw.getOFFactory().buildMatch().build();
				req = sw.getOFFactory().buildFlowStatsRequest()
						.setMatch(match)
						.setOutPort(OFPort.ANY)
						.setTableId(TableId.ALL)
						.build();
				result.setStatType(OFStatsType.FLOW);
				break;
				
			case AGGREGATE:
				match = sw.getOFFactory().buildMatch().build();
				req = sw.getOFFactory().buildAggregateStatsRequest()
						.setMatch(match)
						.setOutPort(OFPort.ANY)
						.setTableId(TableId.ALL)
						.build();
				result.setStatType(OFStatsType.AGGREGATE);
				break;
				
			case PORT:
				req = sw.getOFFactory().buildPortStatsRequest()
				.setPortNo(OFPort.ANY)
				.build();
				result.setStatType(OFStatsType.PORT);
				break;
				
			case QUEUE:
				req = sw.getOFFactory().buildQueueStatsRequest()
				.setPortNo(OFPort.ANY)
				.setQueueId(UnsignedLong.MAX_VALUE.longValue())
				.build();
				result.setStatType(OFStatsType.QUEUE);
				break;
				
			case DESC:
				// pass - nothing todo besides set the type above
				req = sw.getOFFactory().buildDescStatsRequest()
				.build();
				result.setStatType(OFStatsType.DESC);
				break;
				
			case GROUP:
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
					req = sw.getOFFactory().buildGroupStatsRequest()				
							.build();
					result.setStatType(OFStatsType.GROUP);
				}
				break;

			case METER:
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
					req = sw.getOFFactory().buildMeterStatsRequest()
							.setMeterId(OFMeterSerializerVer13.ALL_VAL)
							.build();
					result.setStatType(OFStatsType.METER);
				}
				break;

			case GROUP_DESC:			
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
					req = sw.getOFFactory().buildGroupDescStatsRequest()			
							.build();
					result.setStatType(OFStatsType.GROUP_DESC);
				}
				break;

			case GROUP_FEATURES:
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
					req = sw.getOFFactory().buildGroupFeaturesStatsRequest()
							.build();
					result.setStatType(OFStatsType.GROUP_FEATURES);
				}
				break;

			case METER_CONFIG:
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
					req = sw.getOFFactory().buildMeterConfigStatsRequest()
							.build();
					result.setStatType(OFStatsType.METER_CONFIG);
				}
				break;

			case METER_FEATURES:
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
					req = sw.getOFFactory().buildMeterFeaturesStatsRequest()
							.build();
					result.setStatType(OFStatsType.METER_FEATURES);
				}
				break;

			case TABLE:
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
					req = sw.getOFFactory().buildTableStatsRequest()
							.build();
					result.setStatType(OFStatsType.TABLE);
				}
				break;

			case TABLE_FEATURES:	
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
					req = sw.getOFFactory().buildTableFeaturesStatsRequest()
							.build();	
					result.setStatType(OFStatsType.TABLE_FEATURES);
				}
				break;
				
			case PORT_DESC:
				if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
					req = sw.getOFFactory().buildPortDescStatsRequest()
							.build();
					result.setStatType(OFStatsType.PORT_DESC);
				}
				break;
				
			case EXPERIMENTER: //TODO @Ryan support new OF1.1+ stats types			
			default:
				logger.error("Stats Request Type {} not implemented yet", statsType.name());
				break;
			}			
			try {
				if (req != null) {
					future = sw.writeStatsRequest(req);
					values = (List<OFStatsReply>) future.get(10, TimeUnit.SECONDS);
					//logger.info(values.toString());
					result.setDatapathId(switchId);
					result.setValues(values);
				}
			} catch (Exception e) {
				// TODO: handle exception
				logger.error("Failure retrieving statistics from switch " + sw, e);
			}
		}
		return result;
	}
	
	public void SendPortStaticsRequest(DatapathId swId){
		StatsReply statsReply = SendStaticsRequset(swId, OFStatsType.QUEUE);
		//logger.info(statsReplyq.toString());
		if(statsReply.getStatType() == OFStatsType.QUEUE){
			@SuppressWarnings("unchecked")
			List<OFStatsReply> queueStatsReplylist = (List<OFStatsReply>)statsReply.getValues();
			Iterator<OFStatsReply> replyiter = queueStatsReplylist.iterator();
			while (replyiter.hasNext()) {
				OFQueueStatsReply queueStatsReply = (OFQueueStatsReply) replyiter.next();
				//logger.info(queueStatsReply.toString());
				List<OFQueueStatsEntry> entrylist = queueStatsReply.getEntries();
				Iterator<OFQueueStatsEntry> entryiter = entrylist.iterator();
				long value = 0;
				double rate = 0;
				while (entryiter.hasNext()) {
					OFQueueStatsEntry entry = entryiter.next();
					if(entry.getPortNo().equals(OFPort.of(1))){
						//logger.info(entry.toString());
						if(resolver){
							if(entry.getQueueId()==0){
								logger.info("this's q0 " + entry.toString());
								value = entry.getTxBytes().getValue() - oldQueueTx[0];
								oldQueueTx[0] = entry.getTxBytes().getValue();
								rate = value/DoubleCount;	
								QueueSpeed[0] = rate;
								logger.info("The q0 rate is:" + String.valueOf(rate));
								SwitchPort sp = new SwitchPort();
								sp.setPort(entry.getPortNo());
								sp.setQueue(0);
								rateMap.put(sp, rate);
							}
							else if(entry.getQueueId()==3){
								logger.info("this's q3 " + entry.toString());
								value = entry.getTxBytes().getValue() - oldQueueTx[3];
								oldQueueTx[3] = entry.getTxBytes().getValue();
								rate = value/DoubleCount;							
								QueueSpeed[3] = rate;
								logger.info("The q3 rate is:" + String.valueOf(rate));
								SwitchPort sp = new SwitchPort();
								sp.setPort(entry.getPortNo());
								sp.setQueue(3);
								rateMap.put(sp, rate);
								videosp = rate;
								if(videosp<=1000)
									flowclock = false;
								else
									flowclock = true;
							}
							else if(entry.getQueueId()==4){
								logger.info("this's q4 " + entry.toString());
								value = entry.getTxBytes().getValue() - oldQueueTx[4];
								oldQueueTx[4] = entry.getTxBytes().getValue();
								rate = value/DoubleCount;							
								QueueSpeed[4] = rate;
								logger.info("The q4 rate is:" + String.valueOf(rate));
								SwitchPort sp = new SwitchPort();
								sp.setPort(entry.getPortNo());
								sp.setQueue(4);
								rateMap.put(sp, rate);
								ftpsp = rate;
								if(resolver==true&&flowclock==true&&QueueSpeed[4]>=80000){
									resolver = false;
									VideoQoSFlow(mySwitch, piqos, cntxqos);
									FtpQoSFlow(mySwitch, piqos, cntxqos);
									//VideoRegularFlow(mySwitch, piqos, cntxqos);
									//FtpRegularFlow(mySwitch, piqos, cntxqos);
								}
							}
						}
						else{
							if(entry.getQueueId()==0){
								logger.info("this's q0 " + entry.toString());
								value = entry.getTxBytes().getValue() - oldQueueTx[0];
								oldQueueTx[0] = entry.getTxBytes().getValue();
								rate = value/DoubleCount;
								QueueSpeed[0] = rate;
								logger.info("The q0 rate is:" + String.valueOf(rate));
								SwitchPort sp = new SwitchPort();
								sp.setPort(entry.getPortNo());
								sp.setQueue(0);
								rateMap.put(sp, rate);
							}
							else if(entry.getQueueId()==1){
								logger.info("this's q1 " + entry.toString());
								value = entry.getTxBytes().getValue() - oldQueueTx[1];
								oldQueueTx[1] = entry.getTxBytes().getValue();
								rate = value/DoubleCount;	
								QueueSpeed[1] = rate;
								logger.info("The q1 rate is:" + String.valueOf(rate));
								SwitchPort sp = new SwitchPort();
								sp.setPort(entry.getPortNo());
								sp.setQueue(1);
								rateMap.put(sp, rate);
								videosp = rate;
								if(videosp<=1000)
									flowclock = false;
								else
									flowclock = true;
								if(resolver==false&&flowclock==false&&QueueSpeed[1]<=1000){
									resolver = true;
									VideoRegularFlow(mySwitch, piqos, cntxqos);
									FtpRegularFlow(mySwitch, piqos, cntxqos);					
								}
							}
							else if(entry.getQueueId()==2){
								logger.info("this's q2 " + entry.toString());
								value = entry.getTxBytes().getValue() - oldQueueTx[2];
								oldQueueTx[2] = entry.getTxBytes().getValue();
								rate = value/DoubleCount;
								QueueSpeed[2] = rate;
								logger.info("The q2 rate is:" + String.valueOf(rate));
								SwitchPort sp = new SwitchPort();
								sp.setPort(entry.getPortNo());
								sp.setQueue(2);
								rateMap.put(sp, rate);
								ftpsp = rate;
							}
						}
					}
				}
			}
		}
		if(videosp!=0||ftpsp!=0){
			if(count >= 19){
				count = 1;
				while(count < 20){
					chart[count-1][0] = chart[count][0];
					chart[count-1][1] = chart[count][1];
					count++;
				}
				count = 19;
			}
			chart[count][0] = videosp;
			chart[count][1] = ftpsp;
			//dataxml(count,videosp,ftpsp);						
			/*int datano = count+1;
			String datanumber;
			if(datano<10)
				datanumber = "0" + datano;
			else
				datanumber = "" + datano;*/
			String datatxt;
			//datatxt = datanumber + "   " + videosp + "   " + ftpsp;
			datatxt = videosp + "   " + ftpsp;
			try{
				writeTxtFile (datatxt);
			}
			catch(IOException e){
				System.out.println("Failure to write to Txt");
			}
			count++;
		}
		
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
	public void switchPortChanged(DatapathId switchId, OFPortDesc port,
			PortChangeType type) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void switchChanged(DatapathId switchId) {
		// TODO Auto-generated method stub
		
	}
	
	public void DataChart()
	{
		
		setTitle(" ");
		scrollPane = new JScrollPane();

		String[] columnNames = { "ID" , "Video" , "FTP" };
		String[][] tableValues = {};  	        

		tableModel = new DefaultTableModel(tableValues, columnNames);  
		table = new JTable(tableModel); 

		int rowno = 0;
		while(rowno < 20){
			Object[] row= {rowno+1,chart[rowno][0],chart[rowno][1]};
			rowno++;
			tableModel.addRow(row);
		}
	        
		table.setRowSorter(new TableRowSorter<DefaultTableModel>(tableModel));  
		scrollPane.setViewportView(table);  
		getContentPane().add(scrollPane, BorderLayout.CENTER);  

		setBounds(300, 200, 400, 400);  
		setVisible(true);  
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);  
	        	        
	}
	
	public void Refresh(){ 
		String[] columnNames = { "ID" , "Video" , "FTP" };
		String[][] tableValues = {};
				tableModel = new DefaultTableModel(tableValues, columnNames);  
				table = new JTable(tableModel); 

				int rowno = 0;
				while(rowno < 20){
					Object[] row= {rowno+1,chart[rowno][0],chart[rowno][1]};
					rowno++;
					tableModel.addRow(row);
				}
			        
				table.setRowSorter(new TableRowSorter<DefaultTableModel>(tableModel));  
				scrollPane.setViewportView(table);  
				getContentPane().add(scrollPane, BorderLayout.CENTER); 
	}
	
	public static void dataxml(int no,double video,double ftp)  
    {  
       Object videospeed = video;
       Object ftpspeed = ftp;
       //OutputStream out;   
       try {   
	       //out = new FileOutputStream("MyXml.xml");   
	       java.beans.XMLEncoder encoder = new XMLEncoder(out);   
	       encoder.writeObject(videospeed);   
	       encoder.writeObject(ftpspeed);
	       encoder.close();   
	       List<Object> objList =  BandwidthGuarantee.objectXMLDecoder();  
	       System.out.println(objList.size());
       } catch (Exception e) {   
    	   e.printStackTrace();   
       }   
    }  
	
    public static List<Object> objectXMLDecoder() throws FileNotFoundException,IOException,Exception   
    {   
	     List<Object> objList = new ArrayList<Object>(); 
	     FileInputStream fis = new FileInputStream(fin);   
	     XMLDecoder decoder = new XMLDecoder(fis);   
	     Object obj = null;   
	     try   
	     {   
	    	 while( (obj = decoder.readObject()) != null)   
	    	 {   
	    		 objList.add(obj);   
	    	 }   
	     }   
	     catch (Exception e)   
	     {   
	      // TODO Auto-generated catch block       
	     }   
	     fis.close();   
	     decoder.close();        
	     return objList;   
    }   

    public static boolean writeTxtFile (String newStr) throws IOException  { 
	    boolean flag = false; 
	    String filein = newStr + "\r\n"; 
	    String temp = ""; 
	
	    FileInputStream fis = null; 
	    InputStreamReader isr = null; 
	    BufferedReader br = null; 
	
	    FileOutputStream fos = null; 
	    PrintWriter pw = null; 
	    
	    try { 
		    File file = new File(filenameTemp); 
		    fis = new FileInputStream(file); 
		    isr = new InputStreamReader(fis); 
		    br = new BufferedReader(isr); 
		    StringBuffer buf = new StringBuffer(); 
		
		    for (; (temp = br.readLine()) != null;) { 
			    buf = buf.append(temp); 
			    // System.getProperty("line.separator") 
			    buf = buf.append(System.getProperty("line.separator")); 
		    } 
		    buf.append(filein); 
		
		    fos = new FileOutputStream(file); 
		    pw = new PrintWriter(fos); 
		    pw.write(buf.toString().toCharArray()); 
		    pw.flush(); 
		    flag = true; 
	    } catch (IOException e1) { 
	    	throw e1; 
	    } finally { 
		    if (pw != null) { 
		    	pw.close(); 
		    } 
		    if (fos != null) { 
		    	fos.close(); 
		    } 
		    if (br != null) { 
		    	br.close(); 
		    } 
		    if (isr != null) { 
		    	isr.close(); 
		    } 
		    if (fis != null) { 
		    	fis.close(); 
		    }
	    } 	    
	    return flag; 
    } 
	
}