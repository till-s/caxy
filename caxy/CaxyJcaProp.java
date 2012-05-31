package caxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.util.Properties;

/* Try to mimic what JCA is doing. However, we cannot
 *  - get resources built into JCA. This can only be remedied
 *    by copying such built-in resources to caxy and repackaging
 *    caxy.
 *  - know what 'Context' JCA is using. Hence, we introduce
 *    the '-J' option which lets the user set the name of
 *    the 'Context'.
 */
class CaxyJcaProp {

		protected String jcaCtxtName;

		protected static final String jcaDfltCtxtName = "gov.aps.jca.Context";

		protected static final int    USR_PROP = 0;
		protected static final int    SYS_PROP = 1;
		protected static final int    JCA_PROP = 2;

		protected Properties          props[] = { new Properties(), new Properties(), new Properties() };
		protected String              propn[] = { "builtin",        null,             null             };

		protected boolean             debug;

		public CaxyJcaProp(String jcaCtxtName, boolean debug)
			throws IOException
		{
		InputStream is;
		String pathSep = System.getProperty( "file.separator" );

			this.jcaCtxtName = jcaCtxtName;
			this.debug       = debug;

			try {
				is = CaxyJcaProp.class.getResourceAsStream( "JCALibrary.properties" );
				if ( null == is ) {
					throw new RuntimeException("not found");
				}
				props[JCA_PROP].load( is );
			} catch ( Throwable e ) {
				System.err.println("Unable to load built-in resources: " + e.getMessage());
			}

			try {
				propn[SYS_PROP] = System.getProperty( "java.home" ) + pathSep + "lib" + pathSep + "JCALibrary.properties";
				props[SYS_PROP].load( new FileInputStream( propn[SYS_PROP] ) );
			} catch ( Throwable e ) {
				/* silently ignore */
			}

			try {
				propn[USR_PROP] = System.getProperty( "gov.aps.jca.JCALibrary.properties", null );
				if ( null == propn[USR_PROP] ) {
					propn[USR_PROP] = System.getProperty( "user.home" ) + pathSep + ".JCALibrary" + pathSep + "JCALibrary.properties";
				}
				props[USR_PROP].load( new FileInputStream( propn[USR_PROP] ) );
			} catch ( Throwable e ) {
			}
		}

		public CaxyJcaProp(String jcaCtxtName)
			throws IOException
		{
			this( jcaCtxtName, false );
		}

		public String getJcaProperty(String key, String def)
		{
		String rval = null;

			if ( debug )
				System.err.format("JCA Property; original key '%s'\n", key);

			if ( null != jcaCtxtName ) {
				if ( debug )
					System.err.println("Trying context prefix from commandline:");
				rval = getProperty( new String( jcaCtxtName + "." +  key ), def);
			}

			if ( null == rval ) {
				if ( debug )
					System.err.println("Trying default context prefix:");
				rval = getProperty( new String( jcaDfltCtxtName + "." + key ), def);
			} 
			return rval;
		}

		public String getJcaProperty(String key)
		{
			return getJcaProperty(key, null);
		}

		public String getProperty(String key, String def)
		{
		String rval;
		int    i;

			if ( debug )
				System.err.format("Property '%s' from ", key);

			rval = System.getProperty( key );
			if ( null != rval ) {
				if ( debug )
					System.err.format("SYSTEM");
			} else {
				for ( i = 0; i < props.length; i++ ) {
					rval = props[i].getProperty( key );
					if ( null != rval ) {
						if ( debug )
							System.err.format("%s", propn[i]);
						break;
					}
				}
			}

			if ( null == rval ) {
				rval = def;
				if ( debug )
					System.err.format("DEFAULT");
			}

			if ( debug )
				System.err.println(": '" + rval + "'");

			return rval;
		}

		public String getProperty(String key)
		{
			return this.getProperty( key, null );
		}

		int getJcaIntProperty(String key, int def)
		{
		String str;
			if ( (str = getJcaProperty(key)) != null ) {
				try {
					return Integer.parseInt(str);
				} catch ( java.lang.NumberFormatException e ) {
					System.err.println("Unable to parse "+key+" property");
					System.exit(1);
				}
			}
			return def;
		}

		boolean getJcaBoolProperty(String key, boolean def)
		{
		String str;
			if ( (str = getJcaProperty(key)) != null ) {
				return Boolean.valueOf(str);
			}
			return def;
		}
}
