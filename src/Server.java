/**
 * Created by sagar on 10/7/17.
 */
import gen_java.FileStore.Processor;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TSimpleServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.transport.TTransportException;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class Server {
    public static int port;
    public static FileOperationHandler handler;
    public static void main(String args[]){
        if (args.length == 0){
            System.out.println("Kindly provide Port number as a first argument.");
            return;
        }
        port = Integer.parseInt(args[0]);
        handler = new FileOperationHandler();
        Processor processor = new Processor(handler);
        try {
            TServerTransport tServerTransport = new TServerSocket(port);
            TServer tServer = new TSimpleServer(new TServer.Args(tServerTransport).processor(processor));
            System.out.println(InetAddress.getLocalHost().getHostAddress() + ":"+ port);
            tServer.serve();
        } catch (TTransportException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }
}