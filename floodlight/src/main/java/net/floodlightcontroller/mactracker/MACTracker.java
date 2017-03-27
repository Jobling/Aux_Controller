package net.floodlightcontroller.mactracker;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.mactracker.MACTrackerResource.serverURL;
import net.floodlightcontroller.core.IFloodlightProviderService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.Set;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.restserver.IRestApiService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;

import jsonreader.JsonReader;

public class MACTracker implements IFloodlightModule, IMACTrackerService, IOFMessageListener{
	protected IFloodlightProviderService floodlightProvider;
	protected Set<Long> macAddresses;
	protected Set<String> serversURL;
	protected IRestApiService restApi;
	protected static Logger logger;
	
	@Override
	public String getName(){
		return MACTracker.class.getSimpleName();
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
	public Collection<Class<? extends IFloodlightService>> getModuleServices(){
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IMACTrackerService.class);
        return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		m.put(IMACTrackerService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
	    Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
	    l.add(IFloodlightProviderService.class);
	    l.add(IRestApiService.class);
	    return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
	    restApi = context.getServiceImpl(IRestApiService.class);
		macAddresses = new ConcurrentSkipListSet<Long>();
	    logger = LoggerFactory.getLogger(MACTracker.class);
		serversURL = new HashSet<String>();
		// serversURL.add("http://127.0.0.1:8080/devices/");
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		restApi.addRestletRoutable(new MACTrackerWebRoutable());
	}
	
	@Override
	public net.floodlightcontroller.core.IListener.Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
	    Long sourceMACHash = eth.getSourceMACAddress().getLong();
	    if(!macAddresses.contains(sourceMACHash)){
	    	logger.info("MAC is {}", sourceMACHash);
	    	for(String serverURL : serversURL){
	    		try{
		    		// GET corresponding ip address;
	    			String url = serverURL.concat(eth.getSourceMACAddress().toString());
		    		JSONObject json = JsonReader.readJsonFromUrl(url);
		    		logger.info("json is {}", json.toString());
		    		
		    		// Add macAddress to list
		    		macAddresses.add(sourceMACHash);
		    		logger.info("MAC Address: {} seen on switch: {}",
		                    eth.getSourceMACAddress().toString(),
		                    sw.getId().toString());
		    		break;
		    	}catch (IOException e){
		    		logger.info("IOException on {}", serverURL);
		    	}catch (JSONException e){
		    		logger.info("JSONException on {}", serverURL);
		    	}
	    	}
	    }
	    return Command.CONTINUE;
	}

	@Override
	public boolean putServerURL(serverURL server){
		String serverURL;
		if(server.hostname != null && server.port != null){
			serverURL = "http://" + server.hostname + ":" + server.port + "/devices/";
			serversURL.add(serverURL);
			logger.info("Added url {}", serverURL);
			return true;
		}else{
			return false;
		}
	}

	@Override
	public Set<String> getServers(){
		return serversURL;
	}
	
    protected void jsonToMACInfo(String json, serverURL url) throws IOException {
        MappingJsonFactory f = new MappingJsonFactory();
        JsonParser jp;
        
        try {
            jp = f.createParser(json);
        } catch (JsonParseException e) {
            throw new IOException(e);
        }
        
        jp.nextToken();
        if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
            throw new IOException("Expected START_OBJECT");
        }
        
        while (jp.nextToken() != JsonToken.END_OBJECT) {
            if (jp.getCurrentToken() != JsonToken.FIELD_NAME) {
                throw new IOException("Expected FIELD_NAME");
            }
            
            String n = jp.getCurrentName();
            jp.nextToken();
            
            if(n.equals("hostname"))
            	url.hostname = jp.getText();
            else if (n.equals("port"))
            	url.port = jp.getText();
            else{ 
            	logger.warn("Unrecognized field {} in " +
            		"parsing network definition", 
            		jp.getText());
            }
        }
        jp.close();
    }
}
