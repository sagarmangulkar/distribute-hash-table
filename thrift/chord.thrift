typedef string UserID

exception gen_java.SystemException {
  1: optional string message
}

struct gen_java.RFileMetadata {
  1: optional string filename;
  2: optional i32 version;
  3: optional UserID owner;
  4: optional string contentHash;
}

struct gen_java.RFile {
  1: optional gen_java.RFileMetadata meta;
  2: optional string content;
}

struct gen_java.NodeID {
  1: string id;
  2: string ip;
  3: i32 port;
}

service gen_java.FileStore {
  void writeFile(1: gen_java.RFile rFile)
    throws (1: gen_java.SystemException systemException),
  
  gen_java.RFile readFile(1: string filename, 2: UserID owner)
    throws (1: gen_java.SystemException systemException),

  void setFingertable(1: list<gen_java.NodeID> node_list),
  
  gen_java.NodeID findSucc(1: string key)
    throws (1: gen_java.SystemException systemException),

  gen_java.NodeID findPred(1: string key)
    throws (1: gen_java.SystemException systemException),

  gen_java.NodeID getNodeSucc()
    throws (1: gen_java.SystemException systemException),

}
