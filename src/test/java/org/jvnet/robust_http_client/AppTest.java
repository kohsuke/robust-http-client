package org.jvnet.robust_http_client;

import junit.framework.TestCase;

import java.net.URL;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import static java.lang.Math.max;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.util.Arrays;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.commons.io.output.NullOutputStream;

public class AppTest extends TestCase
{
    public void test1() throws Exception {
        URL url = new URL("http://archive.apache.org/dist/ant/binaries/apache-ant-1.6.5-bin.zip");
        RetryableHttpStream s = new RetryableHttpStream(url) {
            int retry=0;
            @Override
            protected void shallWeRetry() throws IOException {
                System.out.println("Retrying");
                if(retry++>16)
                    throw new IOException("Too many retries");
            }

            /**
             * Simulate the EOF in the middle.
             */
            @Override
            InputStream getStream(HttpURLConnection con) throws IOException {
                byte[] b = new byte[1024*1024];
                int len=0;
                while(len<b.length) {
                    int r = con.getInputStream().read(b,len,b.length-len);
                    if (r<0)    break;
                    len+=r;
                }
                return new ByteArrayInputStream(b,0,len);
            }
        };

        System.out.println("totalLength="+s.totalLength);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CountingOutputStream cos = new CountingOutputStream(baos);
        DigestInputStream din = new DigestInputStream(s,MessageDigest.getInstance("MD5"));
        IOUtils.copy(din,cos);
        assertEquals(cos.getCount(),s.totalLength);
        byte[] md5 = din.getMessageDigest().digest();

        // do direct download, and compare MD5
        din = new DigestInputStream(url.openStream(),MessageDigest.getInstance("MD5"));
        IOUtils.copy(din,new NullOutputStream());

        assertTrue(Arrays.equals(md5,din.getMessageDigest().digest()));
    }
}
