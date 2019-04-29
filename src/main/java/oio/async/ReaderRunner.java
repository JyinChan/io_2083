package oio.async;

import java.io.IOException;
import java.io.InputStream;

public class ReaderRunner extends Thread {

    private int socketVersion;
    private InputStream source;
    private InputOutputListener listener;

    public ReaderRunner(int socketVersion, InputStream source, InputOutputListener listener) {
        this.socketVersion = socketVersion;
        this.source = source;
        this.listener = listener;
    }

    public void run() {
        try {
            while(!Thread.interrupted()) {
                try {
                    while (!Thread.interrupted()) {
                        listener.onReceiveMessage(read());
                    }
                } catch (IOException ioe) {
                    listener.onReconnection(ioe, socketVersion, (s, v) -> {
                        setSocketVersion(v);
                        setSource(s.getInputStream());
                    });
                }
            }
        } catch(Exception e) {
            listener.onException(e);
        }
    }

    private String read() throws IOException {
        byte header[] = new byte[5];
        byte body[];
        if(source.read(header) == -1)
            throw new IOException("read return -1, maybe lost connection");

        int len = Integer.parseInt(new String(header));
        body = new byte[len];
        if(source.read(body) == -1)
            throw new IOException("read return -1, maybe lost connection");
        return new String(body);
    }

    public void setSource(InputStream source) {
        this.source = source;
    }

    public void setSocketVersion(int socketVersion) {
        this.socketVersion = socketVersion;
    }

}
