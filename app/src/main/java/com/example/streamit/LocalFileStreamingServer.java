package com.example.streamit;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.StringTokenizer;
import android.content.ContentResolver;
import android.content.Context;
import com.example.streamit.DeviceDetailFragment.Controlpath;

import com.example.streamit.DeviceDetailFragment;

/**
 * A single-connection HTTP server that will respond to requests for files and
 * pull them from the application's SD card.
 */
public class LocalFileStreamingServer implements Runnable {
    public static final String TAG = LocalFileStreamingServer.class.getName();

    private int port = 0;
    private boolean isRunning = false;
    private ServerSocket socket;
    private Thread thread;
    private ThreadGroup sessions;
    private File mMovieFile;
    private BufferedWriter peerWriter;
    private Controlpath controlPath;

    /**
     * This server accepts HTTP request and returns files from device.
     */
    public LocalFileStreamingServer(File file, String ip, Controlpath controlPath) {
        mMovieFile = file;
        this.controlPath = controlPath;

        try {
            InetAddress inet = InetAddress.getByName(ip);
            byte[] bytes = inet.getAddress();
            socket = new ServerSocket(0, 5, InetAddress.getByAddress(bytes));
            socket.setSoTimeout(10000);
            Log.d(TAG, "Server started at " + getFileUrl());

        } catch (UnknownHostException e) {
            Log.e(TAG, "Error UnknownHostException server", e);
        } catch (IOException e) {
            Log.e(TAG, "Error IOException server", e);
        }
    }

    /**
     * Prepare the server to start.
     *
     * This only needs to be called once per instance. Once initialized, the
     * server can be started and stopped as needed.
     */

//	public void notifyClient()
//	{
//		try{
//			//send IP and port# to peer
////			String url = "http://" + socket.getInetAddress().getHostAddress() + ":"
////					+ socket.getLocalPort();
//			peerWriter.write(getFileUrl() + "\n");
//			peerWriter.flush();
//			Log.e(TAG, "Notified client of HTTP Server URL: " + getFileUrl());
//		}
//		catch (IOException e)
//		{
//			Log.e(TAG, e.getMessage());
//		}
//	}

//	public String init(String ip, BufferedWriter peerWriter) {
//		this.peerWriter = peerWriter;
//
//		try {
//			InetAddress inet = InetAddress.getByName(ip);
//			Log.e(TAG, "init with ip = " + ip);
//
//			byte[] bytes = inet.getAddress();
//			socket = new ServerSocket(0, 0, InetAddress.getByAddress(bytes));
//			socket.setSoTimeout(10000);
//			Log.d(TAG, "Server started at " + getFileUrl());
//
//		} catch (UnknownHostException e) {
//			Log.e(TAG, "Error UnknownHostException server", e);
//		} catch (IOException e) {
//			Log.e(TAG, "Error IOException server", e);
//		}
//	}

    public String getFileUrl() {
        return "http://" + socket.getInetAddress().getHostAddress() + ":"
                + socket.getLocalPort() + "/" + mMovieFile.getName();
    }

    /**
     * Start the server.
     */
    public void start() {

        thread = new Thread(this, "HTTP Server Thread");
        thread.start();
        isRunning = true;
    }

    /**
     * Stop the server.
     *
     * This stops the thread listening to the port. It may take up to five
     * seconds to close the service and this call blocks until that occurs.
     */
    public void stop() {
        isRunning = false;
        if (thread == null) {
            Log.e(TAG, "Server was stopped without being started.");
            return;
        }
        Log.e(TAG, "Stopping HTTP server.");
        thread.interrupt();
        Log.e(TAG, "Stopping HTTP server sessions.");
        sessions.interrupt();
    }

    /**
     * Determines if the server is running (i.e. has been <code>start</code>ed
     * and has not been <code>stop</code>ed.
     *
     * @return <code>true</code> if the server is running, otherwise
     *         <code>false</code>
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * This is used internally by the server and should not be called directly.
     */
    @Override
    public void run() {
        Log.e(TAG, "HTTP Server running");
        sessions = new ThreadGroup("HTTP Server session threads");
        ExternalResourceDataSource dataSource = new ExternalResourceDataSource(mMovieFile);
        int sessionCount = 0;
        controlPath.sendPort(getFileUrl());

        while (isRunning) {
            try {
                Socket client = socket.accept();
                if (client == null) {
                    Log.e(TAG, "Invalid (null) client socket.");
                    continue;
                }
                Log.e(TAG, "Client connected at " + socket.getLocalPort());

                FileStreamingSession session =  new FileStreamingSession(dataSource, client);
                session.start(sessions, sessionCount++);

            } catch (SocketTimeoutException e) {
                Log.e(TAG, "Waiting for client...");
            } catch (IOException e) {
                Log.e(TAG, "Error connecting to client", e);
            }
        }
        Log.e(TAG, "Server interrupted or stopped. Shutting down.");
    }

    private class FileStreamingSession implements Runnable{

        final private Socket client;
        final private ExternalResourceDataSource dataSource;
        private boolean isRunning;

        FileStreamingSession(ExternalResourceDataSource dataSource, Socket client){

            this.client = client;
            this.dataSource = dataSource;
        }

        public void start(ThreadGroup sessions, int threadCount)
        {
            isRunning = true;
            String threadName = "HTTP Server session - " + threadCount;
            Log.d(TAG, "New " + threadName + " created");
            Thread t = new Thread(sessions, this, threadName);
            t.setDaemon(true);
            t.start();
        }

        public void stop()
        {
            isRunning = false;
//			if (sessions == null) {
//				Log.e(TAG, "HTTP Server sessions were stopped without being started.");
//				return;
//			}
            Log.e(TAG, "Stopping HTTP Server sessions");
//			sessions.interrupt();
        }

        @Override
        public void run(){
            try{
                processRequest();
            } catch (IOException e) {
                Log.e(TAG, "Error read from client", e);
            } finally {
                try{
                    client.close();
                }
                catch (IOException e)
                {
                    Log.d(TAG, "Error in closing client socket");
                }
            }
        }
        /*
         * Sends the HTTP response to the client, including headers (as applicable)
         * and content.
         */
        private void processRequest() throws IllegalStateException, IOException {

            InputStream is = client.getInputStream();
            Log.d(TAG, "Client InputStream valid");
            final int BUFFER_SIZE = 8192;
            byte[] buf = new byte[BUFFER_SIZE];

            int rlen = 0;
            int splitbyte = 0;
            long cbSkip = 0;
            int read;

            boolean seekRequest = false;

            // Read request header
            while ((read = is.read(buf, rlen, buf.length - rlen)) > 0) {
                rlen += read;
                splitbyte = findHeaderEnd(buf, rlen);
                if (splitbyte > 0)
                    break;
            }

            // Create a BufferedReader for parsing the header.
            BufferedReader hin = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(buf, 0, rlen)));
            Properties pre = new Properties();
            Properties parms = new Properties();
            Properties header = new Properties();

            try {
                decodeHeader(hin, pre, parms, header);
            } catch (InterruptedException e) {
                Log.e(TAG, "Exception: " + e.getMessage());
                e.printStackTrace();
            }

            for (Entry<Object, Object> e : pre.entrySet()) {
                Log.e(TAG, "Request Header: " + e.getKey() + " : " + e.getValue());
            }
            for (Entry<Object, Object> e : parms.entrySet()) {
                Log.e(TAG, "Request Header: " + e.getKey() + " : " + e.getValue());
            }
            for (Entry<Object, Object> e : header.entrySet()) {
                Log.e(TAG, "Request Header: " + e.getKey() + " : " + e.getValue());
            }

            // Determine cbSkip
            String range = header.getProperty("range");
            if (range != null) {

                seekRequest = true;
                Log.e(TAG, "Seek range is: " + range);
                range = range.substring(6);
                int charPos = range.indexOf('-');
                if (charPos > 0) {
                    range = range.substring(0, charPos);
                }
                cbSkip = Long.parseLong(range);
                Log.e(TAG, "Seek range found!");
            }

            String headers = "";
            if (seekRequest) {// It is a seek or skip request if there's a Range
                // header
                headers += "HTTP/1.1 206 Partial Content\r\n";
                headers += "Content-Type: " + dataSource.getContentType() + "\r\n";
                headers += "Accept-Ranges: bytes\r\n";
                headers += "Content-Length: " + dataSource.getContentLength(false)
                        + "\r\n";
                headers += "Content-Range: bytes " + cbSkip + "-"
                        + dataSource.getContentLength(true) + "/*\r\n";
                headers += "\r\n";
            } else {
                headers += "HTTP/1.1 200 OK\r\n";
                headers += "Content-Type: " + dataSource.getContentType() + "\r\n";
                headers += "Accept-Ranges: bytes\r\n";
                headers += "Content-Length: " + dataSource.getContentLength(false)
                        + "\r\n";
                headers += "\r\n";
            }

            InputStream data = null;
            try {
                // Send header
                Log.e(TAG, "Sending Header to client");
                byte[] buffer = headers.getBytes();
                client.getOutputStream().write(buffer, 0, buffer.length);
//				Log.e(TAG, "Response Header: " + headers);

                // Send content until the end of file
                Log.e(TAG, "Sending Content to client: " + cbSkip + " bytes skipped");
                int cbSentThisBatch = 0;
                int cbRead;
                data = dataSource.createInputStream();
                data.skip(cbSkip);

                byte[] buff = new byte[1024 * 50];
                while (!Thread.interrupted() && (cbRead = data.read(buff, 0, buff.length)) != -1)
                {
                    client.getOutputStream().write(buff, 0, cbRead);
                    client.getOutputStream().flush();
                    Log.d(TAG, "Sent: " + cbRead + " Bytes\tCumulative: " + cbSentThisBatch + " Bytes");
                    cbSentThisBatch += cbRead;
                }
                if (Thread.interrupted())
                    Log.d(TAG, "HTTP Server session interrupted");
                else
                    Log.d(TAG, "Sending Content Complete: " + cbSentThisBatch + " byte sent");

            } catch (SocketException e) {
                // Ignore when the client breaks connection
                Log.e(TAG, "Ignoring " + e.getMessage());
            } catch (IOException e) {
                Log.e(TAG, "Error getting content stream.", e);
            } catch (Exception e) {
                Log.e(TAG, "Error streaming file content.", e);
            }

        }
        /**
         * Find byte index separating header from body. It must be the last byte of
         * the first two sequential new lines.
         **/
        private int findHeaderEnd(final byte[] buf, int rlen) {
            int splitbyte = 0;
            while (splitbyte + 3 < rlen) {
                if (buf[splitbyte] == '\r' && buf[splitbyte + 1] == '\n'
                        && buf[splitbyte + 2] == '\r' && buf[splitbyte + 3] == '\n')
                    return splitbyte + 4;
                splitbyte++;
            }
            return 0;
        }

        /**
         * Decodes the sent headers and loads the data into java Properties' key -
         * value pairs
         **/
        private void decodeHeader(BufferedReader in, Properties pre,
                                  Properties parms, Properties header) throws InterruptedException {
            try {
                // Read the request line
                String inLine = in.readLine();
                if (inLine == null)
                    return;
                StringTokenizer st = new StringTokenizer(inLine);
                if (!st.hasMoreTokens())
                    Log.e(TAG, "BAD REQUEST: Syntax error. Usage: GET /example/file.html");

                String method = st.nextToken();
                pre.put("method", method);

                if (!st.hasMoreTokens())
                    Log.e(TAG, "BAD REQUEST: Missing URI. Usage: GET /example/file.html");

                String uri = st.nextToken();

                // Decode parameters from the URI
                int qmi = uri.indexOf('?');
                if (qmi >= 0) {
                    decodeParms(uri.substring(qmi + 1), parms);
                    uri = decodePercent(uri.substring(0, qmi));
                } else
                    uri = decodePercent(uri);

                // If there's another token, it's protocol version,
                // followed by HTTP headers. Ignore version but parse headers.
                // NOTE: this now forces header names lowercase since they are
                // case insensitive and vary by client.
                if (st.hasMoreTokens()) {
                    String line = in.readLine();
                    while (line != null && line.trim().length() > 0) {
                        int p = line.indexOf(':');
                        if (p >= 0)
                            header.put(line.substring(0, p).trim().toLowerCase(),
                                    line.substring(p + 1).trim());
                        line = in.readLine();
                    }
                }

                pre.put("uri", uri);
            } catch (IOException ioe) {
                Log.e(TAG,"SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
            }
        }

        /**
         * Decodes parameters in percent-encoded URI-format ( e.g.
         * "name=Jack%20Daniels&pass=Single%20Malt" ) and adds them to given
         * Properties. NOTE: this doesn't support multiple identical keys due to the
         * simplicity of Properties -- if you need multiples, you might want to
         * replace the Properties with a Hashtable of Vectors or such.
         */
        private void decodeParms(String parms, Properties p)
                throws InterruptedException {
            if (parms == null)
                return;

            StringTokenizer st = new StringTokenizer(parms, "&");
            while (st.hasMoreTokens()) {
                String e = st.nextToken();
                int sep = e.indexOf('=');
                if (sep >= 0)
                    p.put(decodePercent(e.substring(0, sep)).trim(),
                            decodePercent(e.substring(sep + 1)));
            }
        }

        /**
         * Decodes the percent encoding scheme. <br/>
         * For example: "an+example%20string" -> "an example string"
         */
        private String decodePercent(String str) throws InterruptedException {
            try {
                StringBuffer sb = new StringBuffer();
                for (int i = 0; i < str.length(); i++) {
                    char c = str.charAt(i);
                    switch (c) {
                        case '+':
                            sb.append(' ');
                            break;
                        case '%':
                            sb.append((char) Integer.parseInt(
                                    str.substring(i + 1, i + 3), 16));
                            i += 2;
                            break;
                        default:
                            sb.append(c);
                            break;
                    }
                }
                return sb.toString();
            } catch (Exception e) {
                Log.e(TAG, "BAD REQUEST: Bad percent-encoding.");
                return null;
            }
        }
    }


    /**
     * provides meta-data and access to a stream for resources on SD card.
     */
    protected class ExternalResourceDataSource {

        private final File movieResource;
        long contentLength;

        public ExternalResourceDataSource(File resource) {
            movieResource = resource;
            contentLength = movieResource.length();
            Log.e(TAG, "ExternalResourceDataSource created with " +
                    "Path: " + mMovieFile.getPath() + " " +
                    "Length: " + contentLength);
        }

        /**
         * Returns a MIME-compatible content type (e.g. "text/html") for the
         * resource. This method must be implemented.
         *
         * @return A MIME content type.
         */
        public String getContentType() {

            String filePath = movieResource.getPath();
            String fileExt = filePath.substring(filePath.lastIndexOf(".") + 1);
            Log.d(TAG, "Content Type = video/" + fileExt);
            return "video/" + fileExt;

//			return "video/mp4";
        }

        /**
         * Creates and opens an input stream that returns the contents of the
         * resource. This method must be implemented.
         *
         * @return An <code>InputStream</code> to access the resource.
         * @throws IOException
         *             If the implementing class produces an error when opening
         *             the stream.
         */
        public InputStream createInputStream() throws IOException {
            // NB: Because createInputStream can only be called once per asset
            // we always create a new file descriptor here.
            FileInputStream inputStream = null;
            try {
                inputStream = new FileInputStream(movieResource);
            } catch (FileNotFoundException e) {
                Log.e(TAG, "File Not Found Exception: " + e.getMessage());
            }
            return inputStream;
        }

        /**
         * Returns the length of resource in bytes.
         *
         * By default this returns -1, which causes no content-type header to be
         * sent to the client. This would make sense for a stream content of
         * unknown or undefined length. If your resource has a defined length
         * you should override this method and return that.
         *
         * @return The length of the resource in bytes.
         */
        public long getContentLength(boolean ignoreSimulation) {
            if (!ignoreSimulation) {
                return -1;
            }
            return contentLength;
        }

    }

}