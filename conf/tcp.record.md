SO_LINGER选项

1、默认情况下关闭
不能确定缓冲区数据能一定发出去（？待测试），是否成功发到接收方（如对方断网宕机）
2、值为零，终止读写，清空缓冲区数据，发送RST
3、不为零，终止读写，进入睡眠，直到（a）收到数据和FIN的确认（b）timeout

测试1缓冲区数据不一定发出去
方案1：构建网络阻塞环境，缓冲区数据量大。待完成！！

方案2：服务端休眠10s，使得客户端和服务端缓冲数据，然后客户端close
测试时：close后进入FIN_WAIT_1状态，在服务端休眠完成时，两端缓冲区不变，待休眠完成，缓冲区数据逐渐减少
客户端缓冲区数据为0时，状态转为FIN_WAIT_2，服务端缓冲区为0时，客户端转态为TIME_WAIT
方案2结论：close前写进缓冲区的数据能成功发送，没有被丢掉

方案3：服务端休眠12s，使得客户端和服务端缓冲数据，然后客户端close，服务端读一个数据休眠2~100s
测试时：同方案2
方案3结论：close在长时间阻塞下也能把未发送数据成功发送

测试2清空缓冲区发送RST
将SO_LINGER设为0

测试3
方案1：服务端休眠12s，使得客户端和服务端缓冲数据，然后客户端close，服务端读一个数据休眠2~100s，SO_lINGER设置为非0
测试时：close阻塞SO_LINGER.VALUE10（毫秒）左右返回，两端缓冲区有数据，服务端完成休眠读取数据，客户端未发送数据成功发送
方案3结论：closetimeout未发送数据并没有被丢弃why???

总结：
无论哪种选项设置，都是无法保证write的数据是否被对端应用层成功接收
1.默认情况下，关闭后，缓冲区的数据一般都能发出去，经过多次测试，未发现被丢弃的
一端写，close，另一端不能收到数据情形
一端写,close,另一端write，导致发出close的一端发出RST，连接直接终止
另一端网络故障宕机等
另一端也发出close 1/在一端write时close 2/在一端close前close 3/在一端close后close
发不出去？？？？？？
2.结果和预设一致
3.timeout情况下，未发现数据丢弃，包括c++版

关闭方式讨论
1.默认关闭和延时关闭（SO_LINGER>0），就目前测试结果分析，都能将缓冲数据发出，延时timeout并无异常，缓冲区数据也无丢去。
两者比较，延时关闭如果可以在timeout前关闭，就可以证明数据一定无丢去且被对端成功读取？不一定
在close过程中，对端写，传来的数据比FIN的确认来得快，close也会提早完成，close前写出的数据仍然无法确认是否成功读取。
2.SO_LINGER=0,设置这种选项，不能确保缓冲区数据是否被丢弃，更无法判断是否被对端成功读取。
3.shutdownOutput方式，允许在FIN_WAIT2状态前可以读，由于这个方法没有任何返回值，也不会阻塞，所以应用层仍然无法得知关闭前
发出的数据是否被成功读取。而关闭后还可以读对端的消息（对端收到FIN前发送），这个作用不大，因为对端收到FIN前write出去的数据
是无法确定是否被成功读取。

综上，对应一般的消息（如请求/应答），不一定要求对方收到，即数据允许丢弃/丢失情况下，任何的关闭方式都是可以的，之间并没有
很大的区别。否则，任何关闭方式都是不确定的。

那么要确保消息发送到对端，是需要应用层的消息确认的

若使用了应用层消息确认，那在关闭前写的几条消息如果不需要确认，使用延时关闭，需要解决下面问题

如何在close前判断有无缓冲数据可以读？（有数据未读时close是发出RST）
如何在收到FIN包时停写？（保证收到FIN包时不因为write而RESET）

一个想法：发送应用层的关闭请求。解决两端进程正常运行、网络可用情况下因为关闭连接造成的数据丢弃/丢失问题
1.一端在关闭前，先发送一个CLOSE请求，在收到CLOSE确认前，可继续读，一定时间内没有收到确认，继续发送CLOSE请求
2.收到CLOSE确认后，调用close关闭
3.另一端收到CLOSE请求，若无数据可写，则确认CLOSE，然后停止读写。

//done - test.java.tcp.close
1 有数据未读时close是发出RST?
    发送的是FIN包，见test1
2 write数据引发Broken Pipe是因为收到FIN包还是因为将数据发送到关闭方引起关闭方发送RESET？
    test1测试可知，被动关闭方收到FIN包并回复ACK，然后发送数据引起主动方发送RST
3 shutdownOutput后, 对端在未调用close前仍然可以发送数据吗？
    test2测试可知，服务端shutdownOutput发送FIN包，客户端需要调用close完成连接断开，在此之前客户端仍可以发送数据，服务端也可以接收数据而不会发出RST