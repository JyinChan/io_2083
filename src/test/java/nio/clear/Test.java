package nio.clear;

import nio.clear.server.IoListener;
import nio.clear.server.NioConnection;
import nio.clear.server.Reactor;
import nio.clear.server.SupportAsyncResultNioC;
import org.apache.log4j.PropertyConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*

关于select空轮询
1、如果在连接状态下（关注读），对端断开连接，服务端没有显示close连接，将出现空轮询

GeneralNioConnection and SupportAsyncResultNioC
GeneralNioConnection 和 ScheduledConnection都是非阻塞异步IO
NioConnection不支持主动获取异步操作结果，通过被动通知得到操作结果
read并非主动，因为它并不知道数据是什么
decode是在内部进行，因为这种连接消息协议必然是一致的（为什么?）

ScheduledConnection是在已知数据内容的情况下进行read，这种情况下消息协议可能不一致，因而它需要主动read
连接对端不会主动发送消息


测试
1、unregister
情况一：suspend and then unregister（select Thread）
结果：cancel key异常 修复
情况二：write and then unregister(select Thread)
结果：引发cancel key异常
2、close
3、suspend
挂起读后，客户端发FIN包，服务端响应ACK，因为服务端没有close连接，客户端会停留在FIN_WAIT2状态，连接没有真正关闭！！！
直到重新关注读，或者主动发消息
那么
    1、SupportAsyncResultNioC suspend后，客户端断掉连接，服务端不做心跳
如何将连接真正关掉


新测试
1/GeneralNioConnection
    心跳（主动或被动）
    applyMark（支持并发）有效性
2/SupportAsyncResultNioC
    bind
    心跳（主动）
    write
    writeAndRead
    applyMark（不支持并发）有效性
    只连接不进行读写，对端关闭
    只连接不进行读写，设置心跳，关闭对端
3/scheduledConnect
    whenComplete（成功或失败）
    get

demo2测试
SingleOrderHandler
1、下单观察SingleOrderHandler
2、kill掉IAPI Adapter， 观察SingleOrderHandler
3、查看sender心跳
4、测试report执行时间，决定是否运行在线程池上

ServerHandler
打印命令执行时间，运行在线程池上效率是否有所提高

问题
1、ServerHandler执行完一个命令（某些）后，连接被断掉？原因：对端主动关闭
2、SingleOrderHandler stop 和 sendCommand并发问题？
3、SingleOrderHandler stop throw ConcurrentModifyException ???
4、Connection关闭通知future问题？修复并通过测试

性能测试
服务端：开启多个端口
客户端：请求建立500个连接
1、只做HB，5s/hb，测试notifyIdleConn和doProcess花费时间
500个连接同时发送hb，耗时1~2ms，单纯遍历0ms
doProcess 500个连接耗时大约为10~15ms

2、客户端1s内随机发送消息(较长），服务端回复消息，测试事件处理和doProcess花费时间

4、测试accept耗时

3、客户端200ms发送一个消息，利用Jprofiler分析

4、客户端1s内随机发送消息（当前事件），服务端回复该消息，客户端计算时间差，对比BIO服务器
只打印delay值大于5ms的消息delay值
粗来观察，当运行一段时间后，BIO服务器打印的机会与本服务端相当（一般为8~10ms），且值相当。在运行初，本服务端由于处理连接建立，所以出现150ms左右的delay值
由于测试都在本机进行，BIO服务器所需要的线程数量大（500+），而客户端需要（1000+），因此可能造成BIO服务器delay值误差

总结：一般来说，尽管连接较多（500），消息频繁（1s内/send），每次就绪的事件数不会过高，就测试情况，为20之内，耗时最差估计保持在3ms内，加上notifyIdleConn耗时（2ms）
和doProcess的耗时（以处理量50算，2ms），可以估算出一个消息的处理延时较坏情况是10ms内
 */
public class Test implements IoListener {

    private final static Logger logger = LoggerFactory.getLogger(Test.class);

    public static void main(String args[]) throws Throwable {
        PropertyConfigurator.configure("conf/log4j.properties");
        Reactor reactor = new Reactor();
        //reactor.addBind("127.0.0.1", 8809, 3, SupportAsyncResultNioC.class, Test.class);
        reactor.start();

        //whenComplete
        reactor.connect("127.0.0.1", 8819, SupportAsyncResultNioC.class, new Test())
                .whenComplete((result) -> {
                    if(result != null) {
                        logger.debug("success complete");
                    } else {
                        logger.debug("failed complete");
                    }
                });
//
//        //get
//        long s = System.currentTimeMillis();
//        Object result = reactor.scheduledConnect("127.0.0.1", 8819, SupportAsyncResultNioC.class, new Test()).get();
//        logger.debug("result[{}], cost[{}]", result, System.currentTimeMillis() - s);

    }

    private SupportAsyncResultNioC bio;

    @Override
    public void connectionOpened(NioConnection connection) {
        logger.debug("[{}] opened", connection);
        connection.keepAlive(NioConnection.WRITE_IDLE_STATUS, 10000, "00000", "OK", true, 30000);

//        bio = (SupportAsyncResultNioC) connection;
//        Thread t = new Thread(() -> {
//            //while(true) {
//            logger.debug("write:"+bio.write("00005helli").get());
//                logger.debug("read:"+bio.writeAndRead("00005hello", "00002hi").get());
//                logger.debug("read:"+bio.writeAndRead("00005hello", "00002hi").get());
//            logger.debug("write:"+bio.write("00005helli").get());
//                logger.debug("read:"+bio.writeAndRead("00005hello", "00002hi").get());
//                logger.debug("read:"+bio.writeAndRead("00005hello", "00002hi").get());
//                logger.debug("read:"+bio.writeAndRead("00005hello", "00002hi").get());
//
//                try {
//                    Thread.sleep(1000);
//                } catch (Exception e) {}
//            //}
//        });
//        t.start();
//        try {
//            Thread.sleep(3);
//        } catch (Exception e) {}
//        bio.close();
    }

    @Override
    public void connectionClosed(NioConnection connection) {
        logger.debug("[{}] closed", connection);
    }

    @Override
    public void messageReceived(NioConnection connection, String msg) {
        logger.debug("received: [{}]", msg);
    }

    @Override
    public void onTimeout(NioConnection connection, int timeoutType) {
        logger.debug("timeout");
    }

    @Override
    public void exceptionCaught(NioConnection connection, Exception e) {
        logger.error("", e);
    }
}
