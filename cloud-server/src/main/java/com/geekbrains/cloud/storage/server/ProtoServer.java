package com.geekbrains.cloud.storage.server;

import com.geekbrains.cloud.storage.common.HandlerForServer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class ProtoServer {
    public void run() throws Exception {
        //Пулы потоков:
        //Обработка входящих подключений (accept)
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        //Сетевая часть (Получить и отправить данные)
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            //Настройка работы сервера
            ServerBootstrap b = new ServerBootstrap();
            //Указываем пулы потоков
            b.group(bossGroup, workerGroup)
                    //Дла подключения клиентов
                    .channel(NioServerSocketChannel.class)
                    //Настройка конвеера (для каждого клиента свой
                    .childHandler(new ChannelInitializer<SocketChannel>() { // (4)
                        @Override
                        public void initChannel(SocketChannel ch) {
                            //Добавляем "квадратик" конвеера
                            ch.pipeline().addLast(new HandlerForServer());
                        }
                    });
                    //.childOption(ChannelOption.SO_KEEPALIVE, true);
            //Запуск вервера на порту 8189
            ChannelFuture f = b.bind(8189).sync();
            //Ожидаем ожидание завершение работы сервера чтобы потом только перейти в finally
            f.channel().closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        new ProtoServer().run();
    }
}
