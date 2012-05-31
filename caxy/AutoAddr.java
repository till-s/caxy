package caxy;

import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.List;
import java.util.LinkedList;
import java.util.ListIterator;
import java.net.SocketException;
import java.net.InterfaceAddress;
import java.net.InetAddress;

class AutoAddr {
	private AutoAddr()
	{
	}

	public static InetAddress [] getList(boolean debug)
	{
	Enumeration      ifs;
	ListIterator     adi;
	NetworkInterface nif;
	InterfaceAddress ifa;
	InetAddress      bca;
	LinkedList<InetAddress> bcl = new LinkedList<InetAddress>();
	InetAddress []   rval;
	int              i;

		if ( debug )
			System.err.println("Assembling 'auto address list'");

		try {
		for ( ifs = NetworkInterface.getNetworkInterfaces(); ifs.hasMoreElements(); ) {
			nif = (NetworkInterface)ifs.nextElement();

			if ( debug )
				System.err.println("Checking interface: "+nif.getDisplayName());

			for ( adi = nif.getInterfaceAddresses().listIterator(); adi.hasNext(); ) {
				ifa = (InterfaceAddress)adi.next();
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

		for ( adi = bcl.listIterator(), i = 0; adi.hasNext(); i++ ) {
			rval[i] = (InetAddress)adi.next();
		}

		return rval;
	}

	public static void main(String []args)
	{
		getList(true);
	}
}
