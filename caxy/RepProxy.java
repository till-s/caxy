package caxy;

import java.io.IOException;
import java.net.InetSocketAddress;

class RepProxy extends ClntProxyPool.ClntProxy {

	private long        lastRepSeen = 0;
	RepSubscriber       subscriber;

	public RepProxy(ClntProxyPool pool, int remote_rport, int rport)
		throws IOException, ClntProxyPoolShutdownException
	{
		pool.super(INSIDE, CaxyConst.INADDR_LOOPBACK, remote_rport, 0);

		start( RepProxy.class );

		subscriber = new RepSubscriber( rport );
	}

	public synchronized long lastRepSeenMs()
	{
		return lastRepSeen;
	}

	public synchronized long sinceLastRepSeenMs()
	{
		return System.currentTimeMillis() - lastRepSeen;
	}

	protected synchronized void setLastRepSeenMs()
	{
		lastRepSeen = System.currentTimeMillis();
	}

	public void cleanup()
	{
		super.cleanup();
		subscriber.interrupt();
	}

	public void run() {
		if ( 0 != (ClntProxyPool.debug & CaxyConst.DEBUG_THREAD) ) {
			System.err.println("Repeater Proxy Thread Start");
		}
		try {
			boolean rep_seen;
			while ( true ) {
				rep_seen = handleUdp();
				if ( rep_seen ) {
					setLastRepSeenMs();
				}
			}
		} catch (Throwable e) {
			if ( 0 != (ClntProxyPool.debug & CaxyConst.DEBUG_THREAD) ) {
				e.printStackTrace(System.err);
			}
		} finally {
			cleanup();
			if ( 0 != (ClntProxyPool.debug & CaxyConst.DEBUG_THREAD) ) {
				System.err.println("Repeater Proxy Thread Exit");
			}
		}
	}

	class RepSubscriber extends Thread {
		protected CaPkt               subscriptionMsg;
		protected InetSocketAddress   subscriptionDst;

		public RepSubscriber(int rport)
		{
			subscriptionMsg = new CaPkt();

			subscriptionMsg.set_m_cmmd( CaPkt.REPEATER_REGISTER );
			subscriptionMsg.set_m_available( CaxyConst.INADDR_LOOPBACK );

			subscriptionDst = new InetSocketAddress( InetConv.int2inet(CaxyConst.INADDR_LOOPBACK), rport );

			start();
		}

		public void run()
		{
		final boolean dbg = ( 0 != (ClntProxyPool.debug & CaxyConst.DEBUG_THREAD) );

			if ( dbg ) {
				System.err.println("Repeater Subscriber Thread Start");
			}
			try {
				while ( ! interrupted() ) {
					subscriptionMsg.out( udpChnl, subscriptionDst );
					try {
						sleep( sinceLastRepSeenMs() < 130000L ? 120000L : 10000L );
					} catch ( java.lang.InterruptedException e ) {
						if ( dbg )
							System.err.println("repSubscriber was interrupted (terminating): " + e );
						break;
					}
				}
			} catch (Throwable t) { 
				if ( dbg )
					System.err.println("repSubscriber terminating: " + t);
			} finally {
				if ( dbg ) {
					System.err.println("Repeater Subscriber Thread Exit");
				}
			}
		}
	}
}
