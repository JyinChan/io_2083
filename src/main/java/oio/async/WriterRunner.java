package oio.async;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ArrayBlockingQueue;

public class WriterRunner extends Thread {

    private int socketVersion;
    private OutputStream target;
    private InputOutputListener listener;

    private ArrayBlockingQueue<Msg> msgQueue;

    public WriterRunner(int socketVersion, OutputStream target, InputOutputListener listener) {
        this.socketVersion = socketVersion;
        this.target = target;
        this.listener = listener;
        msgQueue = new ArrayBlockingQueue<>(1024);
    }

    public void run() {
        try {
            while(!Thread.interrupted()) {
                try {
                    while (!Thread.interrupted()) {
                        Msg msg = msgQueue.take();  //a problem: 当阻塞在此时， 连接断掉后重连，并放入消息
                        if (listener.onCheckBeforeSend(msg)) {
                            syncSend(msg);      //a problem: 进而引起发送失败（用的是旧的socket）
                            listener.onSendSucceed(msg.getContent());
                        }
                    }
                } catch (IOException ioe) {
                    listener.onReconnection(ioe, socketVersion, (s, v) -> {
                        setSocketVersion(v);
                        setTarget(s.getOutputStream());
                    });
                }
            }
        } catch (Exception e) {
            listener.onException(e);
        }
    }

    public void syncSend(Msg msg) throws IOException {
        try {
            target.write(msg.getBytes());
        } catch (IOException ioe) {
            listener.onSendFailed(msg.getOrigin(), "send failed");
            throw ioe;
        }
    }

    public boolean asyncSend(Msg msg) {
        return msgQueue.add(msg);
    }

    public void setTarget(OutputStream target) {
        this.target = target;
    }

    public void setSocketVersion(int socketVersion) {
        this.socketVersion = socketVersion;
    }

}
