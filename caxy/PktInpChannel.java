package caxy;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

public class PktInpChannel {
	protected ReadableByteChannel chnl;

	class IncompleteBufferReadException extends Exception {
		private static final long serialVersionUID = 1904012812499522626L;
		int got, wanted;
		protected IncompleteBufferReadException(int got_in, int wanted_in)
		{
			super( "Buffer not completely read (only " + got_in + " out of " + wanted_in + " bytes)" );
			got = got_in; wanted = wanted_in;
		}
	}

	public PktInpChannel(ReadableByteChannel chnl_in)
	{
		chnl = chnl_in;
	}

	public void close() 
		throws IOException
	{
		chnl.close();
	}

	// I get a warning:
	//   "Resource leak: '<unassigned Closeable value>' is never closed"
	// However, this seems to be bogus since the channel returned by getChannel()
	// actually *is* closeable (and there is a run-time error if the FileInputStream
	// is closed here).
	@SuppressWarnings("resource")
	public static PktInpChannel getStdin()
	{
		return new PktInpChannel( (new FileInputStream(java.io.FileDescriptor.in)).getChannel());
	}


	public void getPkt(ByteBuffer b)
		throws IOException, IncompleteBufferReadException
	{
		int here = b.position();
		while ( b.remaining() > 0 && chnl.read(b) > 0 )
			/* nothing else to do */;
		if ( b.remaining() > 0 )
			throw new IncompleteBufferReadException(b.position() - here, b.limit());
	}
}
