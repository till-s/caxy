package caxy;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.ListIterator;

class AutoAddr {
	private AutoAddr()
	{
	}

	public static InetAddress [] getList(boolean debug)
	{
	Enumeration<NetworkInterface>      ifs;
	ListIterator<InterfaceAddress>     adi;
	NetworkInterface nif;
	InterfaceAddress ifa;
	InetAddress      bca;
	LinkedList<InetAddress> bcl = new LinkedList<InetAddress>();
	ListIterator<InetAddress> bci;
	InetAddress []   rval;
	int              i;

		if ( debug )
			System.err.println("Assembling 'auto address list'");

		try {
		for ( ifs = NetworkInterface.getNetworkInterfaces(); ifs.hasMoreElements(); ) {
			nif = ifs.nextElement();

			if ( debug )
				System.err.println("Checking interface: "+nif.getDisplayName());

			for ( adi = nif.getInterfaceAddresses().listIterator(); adi.hasNext(); ) {
				ifa = adi.next();
				bca = ifa.getBroadcast();

				if ( debug ) {
					System.err.println("  Address:     " + ifa);
					System.err.println("    Broadcast: " + (null == bca ? "NONE" : bca));
				}

				if ( null != bca )
					bcl.add( bca );	
			}

		}
		} catch ( SocketException e ) {
			System.err.println("No interfaces found: "+e.getMessage());
			e.printStackTrace();
		}

		rval = new InetAddress [bcl.size()];

		for ( bci = bcl.listIterator(), i = 0; bci.hasNext(); i++ ) {
			rval[i] = bci.next();
		}

		return rval;
	}

	public static void main(String []args)
	{
		getList(true);
	}
}
