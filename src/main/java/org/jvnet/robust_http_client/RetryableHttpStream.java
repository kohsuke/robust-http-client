package org.jvnet.robust_http_client;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;

/**
 * {@link InputStream} implementation around {@link HttpURLConnection} that automatically reconnects
 * if the connection fails in the middle.
 *
 * @author Kohsuke Kawaguchi
 */
public class RetryableHttpStream extends InputStream {
    /**
     * Where are we downloading from?
     */
    public final URL url;

    /**
     * Proxy, or null none is explicitly given (Java runtime may still decide to use a proxy, though.)
     */
    protected final Proxy proxy;

    /**
     * Total bytes of the entity.
     */
    public final int totalLength;

    /**
     * Number of bytes read so far.
     */
    protected int read;

    /**
     * Current underlying InputStream.
     */
    private InputStream in;

    /**
     * {@link HttpURLConnection} to allow the caller to access HTTP resposne headers.
     * Do not use {@link HttpURLConnection#getInputStream()}, however.
     */
    public final HttpURLConnection connection;

    /**
     * Connects to the given HTTP/HTTPS URL, by using the proxy auto-configured by the Java runtime.
     */
    public RetryableHttpStream(URL url) throws IOException {
        this(url,null);
    }

    /**
     * Connects to the given HTTP/HTTPS URL, by using the specified proxy.
     *
     * @param proxy
     *      To force a direct connection, pass in {@link Proxy#NO_PROXY}.
     */
    public RetryableHttpStream(URL url, Proxy proxy) throws IOException {
        this.url = url;
        if (!url.getProtocol().startsWith("http"))
            throw new IllegalArgumentException(url+" is not an HTTP URL");
        this.proxy = proxy;

        connection = connect(url, proxy);
        totalLength = connection.getContentLength();
        in = getStream(connection);
    }

    /**
     * Hook for tests.
     */
    /*package*/ InputStream getStream(HttpURLConnection con) throws IOException {
        return con.getInputStream();
    }

    private HttpURLConnection connect(URL url, Proxy proxy) throws IOException {
        return (HttpURLConnection)(proxy != null ? url.openConnection(proxy) : url.openConnection());
    }

    /**
     * Reconnect and fast-forward until a desired position.
     */
    private void reconnect() throws IOException {
        while(true) {
            shallWeRetry();

            HttpURLConnection con = connect(url, proxy);
            con.setRequestProperty("Range","bytes="+read+"-");
            con.connect();

            String cr = con.getHeaderField("Content-Range");
            in = getStream(con);
            if (cr!=null && cr.startsWith("bytes "+read+"-")) {
                // server responded with a range
                return;
            } else {
                // server sent us the whole thing again. fast-forward till where we want
                int bytesToSkip=read;
                while(true) {
                    long l = in.skip(bytesToSkip);
                    if (l==0)
                        break; // hit EOF. do it all over again
                    bytesToSkip-=l;
                    if (bytesToSkip==0)
                        return; // fast forward complete
                }
            }
        }
    }

    /**
     * Subclass can override this method to determine if we should continue to retry, or abort.
     *
     * <p>
     * If this method returns normally, we'll retry. By default, this method retries 5 times then quits.
     *
     * @throws IOException
     *      to abort the processing.
     */
    protected void shallWeRetry() throws IOException {
        if (nRetry++>5)
            throw new IOException("Too many failures. Aborting.");
    }
    private int nRetry;


    @Override
    public int read() throws IOException {
        while(true) {
            int ch = in.read();
            if (ch>=0) {
                read++;
                return ch;
            }

            if(read>=totalLength)
                return -1;  // EOF expected

            reconnect();
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        while(true) {
            int r = in.read(b, off, len);
            if (r>=0) {
                read+=r;
                return r;
            }

            if(read>=totalLength)
                return -1;  // EOF expected

            reconnect();
        }
    }
}
