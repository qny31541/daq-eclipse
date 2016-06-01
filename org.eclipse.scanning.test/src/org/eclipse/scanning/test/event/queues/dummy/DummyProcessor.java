package org.eclipse.scanning.test.event.queues.dummy;

import org.eclipse.scanning.api.event.EventException;
import org.eclipse.scanning.api.event.queues.IQueueProcess;
import org.eclipse.scanning.api.event.queues.IQueueProcessor;
import org.eclipse.scanning.api.event.queues.beans.Queueable;
import org.eclipse.scanning.api.event.status.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DummyProcessor <P extends Queueable> implements IQueueProcessor<P> {

	private static final Logger logger = LoggerFactory.getLogger(DummyProcessor.class);

	private IQueueProcess<? extends Queueable> process;
	private boolean terminated = false, executed = false;

	protected P dummy;

	@Override
	public void execute() throws EventException {
		setExecuted();
		if (!(dummy.equals(process.getBean()))) throw new EventException("Beans on QueueProcess and QueueProcessor differ");

		process.broadcast(Status.RUNNING, 0d);

		for (int i = 0; i < 100; i++) {
			if (isTerminated()) {
				process.broadcast(Status.TERMINATED);
				return;
			}

			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				logger.error("Dummy process sleeping failed", e);
				throw new EventException(e);
			}
			System.out.println("DummyProcessor ("+dummy.getClass().getSimpleName()+" - "+dummy.getName()+"): "+dummy.getPercentComplete());
			process.broadcast(new Double(i));
		}
		process.broadcast(Status.COMPLETE, 100d, "Dummy process complete (no software run)");
	}

	@Override
	public void pause() throws EventException {
		// TODO Auto-generated method stub
	}

	@Override
	public void resume() throws EventException {
		// TODO Auto-generated method stub
	}

	@Override
	public void terminate() throws EventException {
		terminated = true;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends Queueable> void setProcessBean(T bean) throws EventException {
		if (isExecuted()) throw new EventException("Cannot change queueProcess after execution started.");
		if (bean.getClass().equals(getBeanClass())) {
			dummy = (P)bean;
		} else {
			throw new EventException("Unsupported bean type");
		}
	}

	@Override
	public void setQueueProcess(IQueueProcess<? extends Queueable> process) throws EventException {
		if (isExecuted()) throw new EventException("Cannot change queueProcess after execution started.");
		this.process = process;
	}

	@Override
	public boolean isExecuted() {
		return executed;
	}

	@Override
	public void setExecuted() {
		executed = true;
	}

	@Override
	public boolean isTerminated() {
		return terminated;
	}

	@Override
	public void setTerminated() {
		terminated = true;
	}

}

