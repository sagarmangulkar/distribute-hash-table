/**
 * Created by sagar on 10/7/17.
 */

import gen_java.FileStore;
import gen_java.NodeID;
import gen_java.RFile;
import gen_java.SystemException;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;

public class FileOperationHandler implements FileStore.Iface{
    private List<NodeID> myFingerTable;
    private HashMap<String, NodeID> fingerTableHashMap = new HashMap<>();
    private HashMap<String, RFile> fileHashMap = new HashMap<>();
    public TTransport tTransport;
    private NodeID currentNodeId;

    private static String SHA256AndBytesToHexConverter(String content) {
        //SHA-256 algorithm
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
        StringBuffer hexString = new StringBuffer();
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if(hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    @Override
    public void writeFile(RFile rFile) throws SystemException, TException {
        String fileKey = SHA256AndBytesToHexConverter(rFile.getMeta().getOwner().concat(":").concat(rFile.getMeta().getFilename()));
        if (fileHashMap.containsKey(fileKey)){
            //if file already present- increment the version
            fileHashMap.get(fileKey).getMeta().setVersion(fileHashMap.get(fileKey).getMeta().getVersion() + 1);
        }
        else {
            rFile.getMeta().setContentHash(SHA256AndBytesToHexConverter(rFile.getContent()));
            rFile.getMeta().setVersion(0);
            fileHashMap.put(fileKey, rFile);
        }
        NodeID succNodeId;
        succNodeId = findSucc(fileKey);
        if (succNodeId.getId().compareTo(currentNodeId.getId()) == 0) {
            PrintWriter writer = null;
            try {
                writer = new PrintWriter(rFile.getMeta().getFilename(), "UTF-8");
                writer.println(rFile.getContent());
                writer.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        else {
            SystemException systemException= new SystemException();
            systemException.setMessage("This server (" + currentNodeId.getIp() + ":" + currentNodeId.getPort() + ") does not own the file's id");
            throw systemException;
        }
    }

    @Override
    public RFile readFile(String filename, String owner) throws SystemException, TException {
        String fileKey = SHA256AndBytesToHexConverter(owner.concat(":").concat(filename));
        NodeID succNodeId;
        succNodeId = findSucc(fileKey);
        if (!(succNodeId.getId().compareTo(currentNodeId.getId()) == 0)) {
            SystemException systemException= new SystemException();
            systemException.setMessage("This server (" + currentNodeId.getIp() + ":" + currentNodeId.getPort() + ") does not own the file's id");
            throw systemException;
        }
        if (!fileHashMap.containsKey(fileKey)){
            SystemException systemException = new SystemException();
            systemException.setMessage("Requested File (" + filename + ") and/or owner (" + owner + ") does not present.");
            throw systemException;
        }
        return fileHashMap.get(fileKey);
    }

    @Override
    public void setFingertable(List<NodeID> node_list) throws TException {
        myFingerTable = node_list;
        for (int i = 0; i <= node_list.size()-1; i++) {
            fingerTableHashMap.put(node_list.get(i).getId(), node_list.get(i));
        }
        //setting current nodeId
        String ipPort;
        String hostAddress = "";
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        ipPort = hostAddress.concat(":" + Server.port);
        String sourceKey = SHA256AndBytesToHexConverter(ipPort);
        currentNodeId = new NodeID(sourceKey, hostAddress, Server.port);
    }

    @Override
    public NodeID findSucc(String key) throws SystemException, TException {
        if (key.compareTo(currentNodeId.getId()) == 0){
            return currentNodeId;
        }
        NodeID predNodeId = findPred(key);
        NodeID succNodeId;
        if (predNodeId.compareTo(currentNodeId) == 0){
            succNodeId = getNodeSucc();
        }
        else {
            FileStore.Client client = getClientByServerPort(predNodeId.getPort());
            succNodeId = client.getNodeSucc();
            tTransport.close();
        }
        return succNodeId;
    }

    @Override
    public NodeID findPred(String key) throws SystemException, TException {
        NodeID predNodeId;
        String nextNodeKey = myFingerTable.get(0).getId();
        String ipPort;
        String hostAddress = "";
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        ipPort = hostAddress.concat(":" + Server.port);
        String sourceKey = SHA256AndBytesToHexConverter(ipPort);

        if ((sourceKey.compareTo(nextNodeKey) < 0) && (nextNodeKey.compareTo(key) >= 0) && (key.compareTo(sourceKey) >= 0)){
            predNodeId = new NodeID(sourceKey, hostAddress, Server.port);
            return predNodeId;
        }
        else if (sourceKey.compareTo(nextNodeKey) > 0){
            //if k is at the end
            if (key.compareTo(sourceKey) >= 0){
                if ((nextNodeKey.compareTo(key) <= 0) && (key.compareTo(sourceKey) >= 0)){
                    predNodeId = new NodeID(sourceKey, hostAddress, Server.port);
                    return predNodeId;
                }
            }
            //if k is at the starting
            else if (key.compareTo(sourceKey) < 0){
                if ((nextNodeKey.compareTo(key) >= 0) && (key.compareTo(sourceKey) <= 0)){
                    predNodeId = new NodeID(sourceKey, hostAddress, Server.port);
                    return predNodeId;
                }
            }
        }

        //hopping
        for (int i = 255; i >= 0; i--) {
            String fingerTableNodeKey = myFingerTable.get(i).getId();
            if (Server.port == fingerTableHashMap.get(fingerTableNodeKey).getPort()){
                continue;
            }
            if (sourceKey.compareTo(fingerTableNodeKey) <= 0 && fingerTableNodeKey.compareTo(key) < 0){
                FileStore.Client client = getClientByServerPort(fingerTableHashMap.get(fingerTableNodeKey).getPort());
                predNodeId = client.findPred(key);
                tTransport.close();
                return predNodeId;
            }
            else if ((sourceKey.compareTo(key) > 0) && (sourceKey.compareTo(fingerTableNodeKey) > 0)){
                if (sourceKey.compareTo(fingerTableNodeKey) >= 0 && fingerTableNodeKey.compareTo(key) < 0){
                    FileStore.Client client = getClientByServerPort(fingerTableHashMap.get(fingerTableNodeKey).getPort());
                    predNodeId = client.findPred(key);
                    tTransport.close();
                    return predNodeId;
                }
            }
            else if (sourceKey.compareTo(key) > 0){
                if ((sourceKey.compareTo(fingerTableNodeKey) <= 0) && (fingerTableNodeKey.compareTo(key) > 0)){
                    FileStore.Client client = getClientByServerPort(fingerTableHashMap.get(fingerTableNodeKey).getPort());
                    predNodeId = client.findPred(key);
                    tTransport.close();
                    return predNodeId;
                }
            }
        }
        return null;
    }

    public FileStore.Client getClientByServerPort(int serverPort){
        FileStore.Client client = null;
        try {
            tTransport = new TSocket(InetAddress.getLocalHost().getHostAddress(), serverPort);
            tTransport.open();
            TProtocol protocol = new TBinaryProtocol(tTransport);
            client = new FileStore.Client(protocol);
        }
        catch (TTransportException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return client;
    }

    @Override
    public NodeID getNodeSucc() throws SystemException, TException {
        String localAddress = "";
        try {
            localAddress = InetAddress.getLocalHost().getHostAddress();
        }
        catch (Exception e){
            SystemException systemException = new SystemException();
            systemException.setMessage("Exception: Finger Table is empty for this server- "
                    + localAddress + ":" + Server.port);
            throw systemException;
        }
        return myFingerTable.get(0);
    }
}
