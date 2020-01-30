# ACE Evaluator

    A tool which can be used to evaluate user access on a  MapR ACE, [Access Control Expression]. 

<b>Summary</b>
    
    MapR supports ACE for specifying autorization on different resources like volumes, files, directories, tables, etc.,
    
    This tool can be used to 
     1. understand how MapR ACEs work.
     2. validate ACE against different users before specifying the ACE for any resource  

<b>Usage</b>
    
      
   <b>#1 . Command Line Usage </b>
    
    mvn clean install
    cd bin
    ./checkACE -user <username> -ace <ace-expr>
    
    ./checkACE -h
    usage: checkACE -ace <ace> [-db <proxy>] -user <user>
    checkAccess for the given user w.r.t the specified ace expression
     -ace <ace>     ACE (Access Control Expression) Ex: 'u:mapr|u:root'
     -db <proxy>    should the ace be checked using db semantics
                    proxy - a proxy user to root/mapr
     -user <user>   username whose access to be evaluated on the ACE
     
        
   <b>#2 . API Usage </b>
   
    AceEvaluator aceEvaluator = new AceEvaluator("username", "ace-expr", true, false);
    if(aceEvaluator.checkAccess())
        System.out.println("allowed");
    else
        System.out.println("denied");
        
<b>Examples </b>
    
    ./checkACE -user m7user1 -ace 'u:m7user1'
    true
    Time (ms) : 715.040773
    
    
    ./checkACE -user m7user1 -ace 'u:m7user2'
    false
    Time (ms) : 736.388597
    
    
        
    ./checkACE -user root -ace u:mapr
    true
    Time (ms) : 542.043318
    
    ./checkACE -user root -ace u:mapr -db m7user1
    false
    Time (ms) : 519.963437      
    
<b> Debugging </b>
    
    
   <b>#1 . -clean</b>     
   
    ./checkACE -user root -ace 'u:0' -clean false
        access check file : /tmp/AceEvaluator-35511359457015990-root
        true
        Time (ms) : 758.090321
        hadoop fs -cat /tmp/AceEvaluator-35511359457015990-root
        u:0
        
   <b>#2 . -d remote debug</b>
    
     ./checkACE -user root -ace 'u:root&u:mapr' -db m7user1 -clean false -d 5005
     Listening for transport dt_socket at address: 5005
     
[Access Control Expression]: https://mapr.com/docs/52/SecurityGuide/SyntaxOfACE.html