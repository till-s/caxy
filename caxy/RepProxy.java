package caxy;

import java.net.InetSocketAddress;
import java.io.IOException;

class RepProxy extends ClntProxy {

	private long        lastRepSeen;

	public RepProxy(int remote_rport, int rport)
		throws IOException
	{
		super(OUTSIDE, CaxyConst.INADDR_LOOPBACK, remote_rport, 0);

		new RepSubscriber( rport );
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

	public void run() {
		try {
			boolean rep_seen;
			while ( true ) {
				rep_seen = handleUdp();
				if ( rep_seen ) {
					setLastRepSeenMs();
				}
			}
		} catch (Throwable e) {
			e.printStackTrace(System.err);
		} finally {
			System.exit(1);
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
			while ( true ) {
				try {
					subscriptionMsg.out( udpChnl, subscriptionDst );
				} catch ( IOException e ) {
					System.err.println("repSubscriber incurred IOException (terminating): " + e);
					break;
				}
				try {
					sleep( sinceLastRepSeenMs() < 120000L ? 120000L : 10000L );
				} catch ( java.lang.InterruptedException e ) {
					System.err.println("repSubscriber was interrupted (terminating): " + e );
					break;
				}
			}
			System.exit(1);
		}
	}
}
