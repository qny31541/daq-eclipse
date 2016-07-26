package org.eclipse.scanning.api.device;

import java.util.Arrays;
import java.util.Collection;
import java.util.logging.Logger;

import org.eclipse.scanning.api.IScannable;
import org.eclipse.scanning.api.annotation.ui.DeviceType;
import org.eclipse.scanning.api.event.EventException;
import org.eclipse.scanning.api.event.core.IPublisher;
import org.eclipse.scanning.api.event.core.IResponseProcess;
import org.eclipse.scanning.api.event.scan.DeviceAction;
import org.eclipse.scanning.api.event.scan.DeviceInformation;
import org.eclipse.scanning.api.event.scan.DeviceRequest;
import org.eclipse.scanning.api.scan.ScanningException;

/**
 * TODO FIXME Is the idea of having request/response calls correct for exposing
 * services on the server to the client?
 * Disadvantages:
 * 1. Pushes to much to client because it has make/receive request/responses.
 * 2. Lot of specific code around a given service in the client. Would be nicer to discover services
 *    using the original service directly, but it going back to the server...
 * 
 * Advantages:
 * 1. Can work for any client, python and javascript included
 * 2. No JSON serialization issues
 * 
 * 
 * @author Matthew Gerring
 *
 */
public class DeviceResponse implements IResponseProcess<DeviceRequest> {
	
	private IRunnableDeviceService    dservice;
	private DeviceRequest             bean;
	private IPublisher<DeviceRequest> publisher;
	private IScannableDeviceService   cservice;

	public DeviceResponse(IRunnableDeviceService  dservice, 
			              IScannableDeviceService cservice, 
			              DeviceRequest           bean, 
			              IPublisher<DeviceRequest> statusNotifier) {
		
		this.dservice = dservice;
		this.cservice = cservice;
		this.bean     = bean;
		this.publisher = statusNotifier;
	}

	@Override
	public DeviceRequest getBean() {
		return bean;
	}

	@Override
	public IPublisher<DeviceRequest> getPublisher() {
		return publisher;
	}

	@Override
	public DeviceRequest process(DeviceRequest request) throws EventException {
		try {
			if (request.getDeviceType()==DeviceType.SCANNABLE) {
				processScannables(request, cservice);
			} else {
				processRunnables(request, dservice);
			}
			return request;
		} catch (ScanningException | InterruptedException ne) {
			throw new EventException("", ne);
		}
	}
	
	private static void processScannables(DeviceRequest request, IScannableDeviceService cservice) throws ScanningException, EventException {
		
		final Collection<String> names = cservice.getScannableNames();
		for (String name : names) {

			if (name==null) continue;
			if (request.getDeviceName()!=null && !name.matches(request.getDeviceName())) continue;

			IScannable<?> device = cservice.getScannable(name);
			if (device==null) throw new EventException("There is no created device called '"+name+"'");

			DeviceInformation<?> info = new DeviceInformation<Object>(name);
			request.addDeviceInformation(info);
		}
	}


	private static void processRunnables(DeviceRequest request, IRunnableDeviceService dservice) throws ScanningException, EventException, InterruptedException {
		
		if (request.getDeviceName()!=null) { // Named device required
			IRunnableDevice<Object> device = dservice.getRunnableDevice(request.getDeviceName());
			if (device==null) throw new EventException("There is no created device called '"+request.getDeviceName()+"'");
			
			if (request.getDeviceModel()!=null) device.configure(request.getDeviceModel());
			
			// TODO We should have a much more reflection based way of
			// calling arbitrary methods. 
			if (request.getDeviceAction()!=null) {
				if (request.getDeviceAction()==DeviceAction.RUN) {
					device.run(request.getPosition());
				} else if (request.getDeviceAction()==DeviceAction.ABORT) {
					device.abort();
				} else if (request.getDeviceAction()==DeviceAction.RESET) {
					device.reset();
				}
			}
			
			DeviceInformation<?> info = ((AbstractRunnableDevice<?>)device).getDeviceInformation();
			request.addDeviceInformation(info);
			
		} else if (request.getDeviceModel()!=null) { // Modelled device created
			
			IRunnableDevice<Object> device = dservice.createRunnableDevice(request.getDeviceModel(), request.isConfigure());
			DeviceInformation<?> info = ((AbstractRunnableDevice<?>)device).getDeviceInformation();
			request.addDeviceInformation(info);
			
		} else {  // Device list needed.
			
			final Collection<String> names = dservice.getRunnableDeviceNames();
			for (String name : names) {
	
				if (name==null) continue;
	
				IRunnableDevice<Object> device = dservice.getRunnableDevice(name);
				if (device==null) {
					System.out.println("Device '"+name+"' could not be found.");
					continue;
				}
				if (!(device instanceof AbstractRunnableDevice)) {
					System.out.println("Device '"+name+"' is not AbstractRunnableDevice and will be ignored.");
					continue;
				}
	
				DeviceInformation<?> info = ((AbstractRunnableDevice<?>)device).getDeviceInformation();
				request.addDeviceInformation(info);
			}
		}
	}
}
