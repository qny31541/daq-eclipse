package org.eclipse.scanning.malcolm.core;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.scanning.api.malcolm.IMalcolmDevice;
import org.eclipse.scanning.api.malcolm.MalcolmDeviceException;
import org.eclipse.scanning.api.malcolm.connector.IMalcolmConnectorService;
import org.eclipse.scanning.api.malcolm.connector.MessageGenerator;
import org.eclipse.scanning.api.malcolm.event.IMalcolmListener;
import org.eclipse.scanning.api.malcolm.event.MalcolmEventBean;
import org.eclipse.scanning.api.malcolm.message.JsonMessage;
import org.eclipse.scanning.api.points.IPosition;
import org.eclipse.scanning.api.scan.AbstractRunnableDevice;
import org.eclipse.scanning.api.scan.ScanningException;
import org.eclipse.scanning.api.scan.event.IRunListener;
import org.eclipse.scanning.api.scan.event.RunEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO FIXME This class contains some things that will be dealt with by Malcolm like the ReentrantLock
// They will be removed eventually... For now this is part of the Mock and it is not clear if the real
// Malcolm connection will need to deal with locking (depending on wether it passes the tests or not!)


/**
 * Base class for non-pausable Malcolm devices
 *
 */
public abstract class AbstractMalcolmDevice<T> implements IMalcolmDevice<T> {
	
	private static final Logger logger = LoggerFactory.getLogger(AbstractMalcolmDevice.class);

	// Fields
	protected String              name;
	protected int                 level;
	
	// Events
	protected MalcolmEventDelegate eventDelegate;
	
	// Connection to serilization to talk to the remote object
	protected MessageGenerator<JsonMessage> connectionDelegate;
	
	public AbstractMalcolmDevice(IMalcolmConnectorService<JsonMessage> connector) throws MalcolmDeviceException {
   		this.connectionDelegate = connector.createDeviceConnection(this);
   		this.eventDelegate = new MalcolmEventDelegate(name, connector);
	}
		
	/**
	 * Enacts any pre-actions or conditions before the device attempts to run the task block.
	 *  
	 * @throws Exception
	 */
	protected void beforeExecute() throws Exception {
        logger.debug("Entering beforeExecute, state is " + getState());	
	}
	
	
	/**
	 * Enacts any post-actions or conditions after the device completes a run of the task block.
	 *  
	 * @throws Exception
	 */
	protected void afterExecute() throws Exception {
        logger.debug("Entering afterExecute, state is " + getState());	
	}
	
	protected void setTemplateBean(MalcolmEventBean bean) {
		eventDelegate.setTemplateBean(bean);
	}
	
	public void start(final IPosition pos) throws ScanningException, InterruptedException {
		
		final List<Throwable> exceptions = new ArrayList<>(1);
		final Thread thread = new Thread(new Runnable() {
			public void run() {
				try {
					AbstractMalcolmDevice.this.run(pos);
				} catch (ScanningException|InterruptedException e) {
					e.printStackTrace();
					exceptions.add(e);
				}
			}
		}, "Device Runner Thread "+getName());
		thread.start();
		
		// We delay by 500ms just so that we can 
		// immediately throw any connection exceptions
		Thread.sleep(500);
		
		if (!exceptions.isEmpty()) throw new ScanningException(exceptions.get(0));
	}

	/**
	 * Not supported in non-pausable device
	 */
	@Override
	public void pause() throws MalcolmDeviceException {
		throw new MalcolmDeviceException(this, "This device is not pausable");
	}

	/**
	 * Not supported in non-pausable device
	 */
	@Override
	public void resume() throws MalcolmDeviceException {
		throw new MalcolmDeviceException(this, "This device is not resumable");
	}


	protected void close() throws Exception {
		eventDelegate.close();
	}
	
	@Override
	public void dispose() throws MalcolmDeviceException {
		try {
			try {
			    if (getState().isRunning()) abort();
			} finally {
			   close();
			}
		} catch (Exception e) {
			throw new MalcolmDeviceException(this, "Cannot dispose of '"+getName()+"'!", e);
		}
	}


	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public void addMalcolmListener(IMalcolmListener l) {
		eventDelegate.addMalcolmListener(l);
	}

	@Override
	public void removeMalcolmListener(IMalcolmListener l) {
		eventDelegate.removeMalcolmListener(l);
	}
	
	@Override
	public void addRunListener(IRunListener l) {
		eventDelegate.addRunListener(l);
	}

	@Override
	public void removeRunListener(IRunListener l) {
		eventDelegate.removeRunListener(l);
	}
	
	@Override
	public void fireRunWillPerform(IPosition position) throws ScanningException{
		eventDelegate.fireRunWillPerform(this, position);
	}
	
	@Override
	public void fireRunPerformed(IPosition position) throws ScanningException{
		eventDelegate.fireRunPerformed(this, position);
	}
	
	@Override
	public void fireWriteWillPerform(IPosition position) throws ScanningException{
		eventDelegate.fireWriteWillPerform(this, position);
	}
	
	@Override
	public void fireWritePerformed(IPosition position) throws ScanningException{
		eventDelegate.fireWriteWillPerform(this, position);
	}


	@Override
	public void configure(T params) throws MalcolmDeviceException {
		throw new MalcolmDeviceException(this, "Configure has not been implemented!");
	}

	protected void sendEvent(MalcolmEventBean event) throws Exception {
		eventDelegate.sendEvent(event);
	}


	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AbstractMalcolmDevice other = (AbstractMalcolmDevice) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

	public int getLevel() {
		return level;
	}

	public void setLevel(int level) {
		this.level = level;
	}

}
