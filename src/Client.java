/**
 * Created by sagar on 10/7/17.
 */
import gen_java.FileStore;
import gen_java.RFile;
import gen_java.RFileMetadata;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class Client {
    public static void main(String args[]){
        int sourcePort = Integer.parseInt(args[0]);
        int keyPort = Integer.parseInt(args[1]);
        try {
            TTransport tTransport = new TSocket(InetAddress.getLocalHost().getHostAddress(), sourcePort);
            tTransport.open();
            TProtocol protocol = new TBinaryProtocol(tTransport);
            FileStore.Client client = new FileStore.Client(protocol);
            perform(client, sourcePort, keyPort);
        } catch (TTransportException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    private static void perform(FileStore.Client client, int sourcePort, int keyPort) {
        RFileMetadata rFileMetadata = new RFileMetadata();
        RFile rFileWriter = new RFile();
        RFile rFileReader;
        rFileWriter.setMeta(rFileMetadata);
        try {
            if (keyPort == 9090) {
                //writing
                rFileMetadata.setFilename("TempFileCreatedAtOtherServer.txt");
                rFileMetadata.setOwner("half__Monster");
                rFileWriter.setContent("Hello, I am created file...!");
                client.writeFile(rFileWriter);
            }
            if (keyPort == 9091) {
                //reading
                rFileReader = client.readFile("TempFileCreatedAtOtherServer.txt", "half__Monster");
                System.out.println("\nFile Name: " + rFileReader.getMeta().getFilename());
                System.out.println("File Owner: " + rFileReader.getMeta().getOwner());
                System.out.println("File version: " + rFileReader.getMeta().getVersion());
                System.out.println("File Content: " + rFileReader.getContent());
                System.out.println("File Content Hash: " + rFileReader.getMeta().getContentHash());
                System.out.println();
            }
        } catch (TException e) {
            e.printStackTrace();
        }
    }
}