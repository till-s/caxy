package caxy;

import java.net.NetworkInterface;
import java.util.Enumeration;
import java.net.SocketException;
import java.net.InetAddress;

class AutoAddr {
	private AutoAddr()
	{
	}

	public static void getList()
	{
	Enumeration      ifs, ads;
	NetworkInterface nif;
	InetAddress      ina;

		try {
		for ( ifs = NetworkInterface.getNetworkInterfaces(); ifs.hasMoreElements(); ) {
			nif = (NetworkInterface)ifs.nextElement();

			System.err.println("IF: "+nif.getDisplayName());

			for ( ads = nif.getInetAddresses(); ads.hasMoreElements(); ) {
				ina = (InetAddress)ads.nextElement();
				System.err.println("  Addr: " + ina);
			}

		}
		} catch ( SocketException e ) {
			System.err.println("No interfaces found: "+e.getMessage());
			e.printStackTrace();
		}
	}

	public static void main(String []args)
	{
		getList();
	}
}
