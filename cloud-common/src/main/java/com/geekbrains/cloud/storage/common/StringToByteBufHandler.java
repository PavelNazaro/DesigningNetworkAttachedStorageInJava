package com.geekbrains.cloud.storage.common;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultFileRegion;

public class StringToByteBufHandler extends ChannelOutboundHandlerAdapter {
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        String str = "";
        if (msg instanceof String){
            str = (String) msg;
        } else if (msg instanceof ByteBuf){
            byte b = ((ByteBuf) msg).readByte();
            str = String.valueOf(b);
        } else if (msg instanceof Long){
            str = String.valueOf(msg);
        } else if (msg instanceof Byte){
            ctx.writeAndFlush(msg);
            return;
        }

        byte[] arr = (str + "\n").getBytes();
        ByteBuf buf = ctx.alloc().buffer(arr.length);
        buf.writeBytes(arr);
        ctx.writeAndFlush(buf);
    }
}
