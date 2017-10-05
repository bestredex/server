
EON peer core source code.


Description
-----------

EON is a decentralized blockchain based-platform that provides an 
infrastructure for the Exscudo Ecosystem services. The  architecture  of  the  
platform  is  built  on  a  simple  core  that realizes a mathematical model and 
services that provide additional functionality. 

The core forms the decentralized part of the system that consists of a variety 
of peers and executes the functionality of support on user account and financial 
operations.

This repository contains the implementation of the peer.


How to build 
------------

Follows the standard Maven building procedure (see https://maven.apache.org/).
```bash
mvn package
```

Run embedded server:
```bash
mvn tomcat7:run
```

Run with setting generation account:
```bash
mvn tomcat7:run -DSECRET_SEED=...
```

Or build and run docker-image
```bash
docker build -t eon/peer .
docker run -d -v $(pwd)/db:/app/db -p 9443:9443 -e SECRET_SEED=... eon/peer
```


Directory Layout 
----------------

**peer-core** - Core of the node without binding to the organization of data storage and the implementation of the transport.

**peer-eon** - EON-specific code. Contains implementation of transaction handlers, EDS algorithm end etc.

**peer-eon-app** - Implementation of simple bot API and JSON-RPC transport.

**peer-store-sqlite** - Simple data storage in SQLite and corresponding algorithms. 


License
-------

Project is issued under GNU LESSER GENERAL PUBLIC LICENSE version 3.0
Uses the library under MIT License ( https://github.com/InstantWebP2P/tweetnacl-java/blob/master/LICENSE ).
