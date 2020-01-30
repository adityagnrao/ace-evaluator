package com.mapr.tools;
import com.mapr.fs.MapRFileAce;
import com.mapr.fs.MapRFileSystem;
import com.mapr.fs.ShimLoader;
import org.apache.commons.cli.*;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.security.UserGroupInformation;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;

import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.regex.Pattern;

import static java.lang.System.exit;

/**
 * A tool to evaluate MapR ACE
 */
@NotThreadSafe
public class AceEvaluator {

    static {
        ShimLoader.load();
    }


    private String user;
    private String proxyDbUser = "mapruser1";
    private String ace;
    private boolean clean = true;
    private boolean isDb = false;

    private long startTime;
    private String fileName = "/tmp/AceEvaluator";

    private static Options inputOptions;
    private static Options printOptions;
    private static String USER = "user";
    private static String ACE = "ace";
    private static String CLEAN = "clean";
    private static String DB = "db";
    private static String PROXY = "proxy";

    //can only be used by cmd line invocation
    protected AceEvaluator (){
        inputOptions = new Options();
        printOptions = new Options();
        startTime = System.nanoTime();
    }

    /**
     *  API access to AceEvaluator,
     *  default user, ace, isDb and clean options can be reset while calling checkAccess
     *
     * @param pUser
     * @param pIsDb
     */
    public AceEvaluator(String pUser, boolean pIsDb) {
        this.user = pUser;
        this.isDb = pIsDb;
        startTime = System.nanoTime();
    }

    /**
     * API access to AceEvaluator,
     * default user, ace, isDb and clean options can be reset while calling checkAccess
     *
     * @param pUser
     * @param pAce
     * @param pClean
     */
    public  AceEvaluator(String pUser, String pAce, boolean pClean, boolean pIsDb) {
        this.user = pUser;
        this.ace = pAce;
        this.clean = pClean;
        this.isDb = pIsDb;
        startTime = System.nanoTime();
    }

    public static void main(String[] args) {
        AceEvaluator aceEvaluator = new AceEvaluator();

        try {

            //parse args
            if(aceEvaluator.parseArgs(args)) {
                //checkAccess always cleanup from cmd line usage
                System.out.println(aceEvaluator.checkAccess(aceEvaluator.user, aceEvaluator.ace, aceEvaluator.clean
                        , aceEvaluator.isDb, aceEvaluator.proxyDbUser));
            } else {
                printHelp("Please Check the Arguments passed");
                exit(-1);
            }
            System.out.println("Time (ms) : "
                    + ((double)(System.nanoTime() - aceEvaluator.startTime))/1000000);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private boolean parseArgs(String args[]) throws Exception {
        boolean parsingComplete = false;
        CommandLine parser = null;
        initOptions();
        if(args.length == 0
                || args[0].equalsIgnoreCase("-help")
                || args[0].equalsIgnoreCase("-h")
                || args[0].equalsIgnoreCase("--h")
        ) {

            printHelp(null);
            exit(0);

        } else if (args[0].equalsIgnoreCase("-" + USER)
                || args[0].equalsIgnoreCase("-" + ACE)) {

            parser = new GnuParser().parse(inputOptions, args);
            user = parser.getOptionValue(USER);
            if(user != null && !Pattern.matches("^[a-z_]([a-z0-9_-]{0,31}|[a-z0-9_-]{0,30}\\$)$", user)) {
                printHelp("Invalid username provided");
                exit(-1);
            }

            ace = parser.getOptionValue(ACE);
            if(ace != null && !MapRFileAce.IsBooleanExpressionValid(ace)){
                printHelp("Invalid ACE expression provided");
                exit(-1);
            }

            clean = Boolean.parseBoolean(parser.getOptionValue(CLEAN, "true"));
            isDb = parser.hasOption(DB);
            if(isDb) {
                proxyDbUser = parser.getOptionValue(DB);
                if(proxyDbUser != null &&
                        !Pattern.matches("^[a-z_]([a-z0-9_-]{0,31}|[a-z0-9_-]{0,30}\\$)$", proxyDbUser)) {
                    printHelp("Invalid proxy user provided - should be an existing linux user");
                    exit(-1);
                }
            }

            if(user != null && ace != null) {
                parsingComplete = true;
            }


        } else {
            printHelp("Invalid username/ace provided");
            exit(-1);
        }


        return parsingComplete;
    }


    /**
     * checkAccess of the default user set on the default ace
     *
     * @return true if allowed access, false otherwise
     * @throws Exception
     */
    public boolean checkAccess() throws Exception {
        return checkAccess(this.user, this.ace, this.clean, this.isDb, this.proxyDbUser);
    }

    /**
     * checkAccess of the default user set on the ace specified
     *
     * @return
     * @throws Exception
     */
    public boolean checkAccess(@Nonnull String pAce) throws Exception {
        return checkAccess(this.user, pAce, this.clean, this.isDb, this.proxyDbUser);
    }

    /**
     * checkAccess for the given user w.r.t the specified ace expression
     *
     * @param pUser
     * @param pAce
     * @param pCleanUpFile - if set to true, removes the temporary file created
     * @param pIsDb
     * @param pProxyDbUser
     * @return true if allowed access, false otherwise
     * @throws Exception
     */
    public boolean checkAccess(
            @Nonnull String pUser
            , @Nonnull String pAce
            , final boolean pCleanUpFile
            , boolean pIsDb
            , String pProxyDbUser) throws Exception {
        boolean lOut = false;

        startTime = System.nanoTime();


        if(!validateAccessParams(pUser, pAce))
            throw new IllegalArgumentException("user : " + pUser + " ace : " + pAce);

        //handle isDB
        String lUser = pUser;
        String lAce = pAce;
        if(pIsDb && (pUser.equalsIgnoreCase("root")||pUser.equalsIgnoreCase("mapr"))) {

            if(pProxyDbUser == null || pProxyDbUser == "")
                pProxyDbUser = "mapruser1";

            lUser = pProxyDbUser;

            if(pUser.equalsIgnoreCase("root"))
                lAce = pAce.replaceAll(":root(?![a-z_0-9-])", ":" + pProxyDbUser);
            else
                lAce = pAce.replaceAll(":mapr(?![a-z_0-9-])", ":" + pProxyDbUser);
        }

        //build ace list
        final ArrayList<MapRFileAce> aces = new ArrayList<MapRFileAce>();
        MapRFileAce mfsWriteAce = new MapRFileAce(MapRFileAce.AccessType.WRITEFILE);

        mfsWriteAce.setBooleanExpression(lAce);
        aces.add(mfsWriteAce);

        //create a new file and set ace as CURRENT_USER (usually root)
        final Path lFilePath = new Path(fileName + "-" + startTime + "-" + lUser);

        try {

            //try to write as the given pUser, return true if allowed or false if not.
            UserGroupInformation ugi1 = UserGroupInformation.createProxyUser(
                    lUser, UserGroupInformation.getLoginUser());
            final String finalLAce = lAce;
            ugi1.doAs(new PrivilegedExceptionAction<Void>() {
                public Void run() throws Exception {
                    Configuration conf = new Configuration();
                    MapRFileSystem mfs = (MapRFileSystem)(FileSystem.get(conf));
                    FSDataOutputStream lStream = null;
                    try {

                        //create the tmp file
                        mfs.create(lFilePath, true).close();

                        //set ace
                        mfs.setAces(lFilePath, aces);

                        //reopen the file for append
                        lStream = mfs.append(lFilePath);

                        //write to stream
                        lStream.writeBytes(finalLAce + "\n");

                        //if denied access, close throws an exception
                        if(lStream != null)
                            lStream.close();

                    } catch (Exception e) {
                        throw e;
                    }finally {

                        if(mfs != null) {

                            //cleanup the tmp file
                            if (pCleanUpFile) {
                                mfs.delete(lFilePath, true);
                            } else {
                                System.out.println("access check file : " + lFilePath);
                            }
                            mfs.close();
                        }
                    }
                    return null;
                }
            });

            //set return value to true if no exception (was given access to append)
            lOut = true;

        } catch (Exception e1) {
            //e1.printStackTrace();
            //expected exception in case of access denial
        }

        return lOut;
    }

    /**
     * validates if the user and ace are not empty
     * and also checks if the username specified a valid linux username pattern
     *
     * @param pUser
     * @param pAce
     * @return
     */
    private boolean validateAccessParams( @Nonnull String pUser
            , @Nonnull String pAce) {

        if(pUser == null || pAce == null
                || !Pattern.matches("^[a-z_]([a-z0-9_-]{0,31}|[a-z0-9_-]{0,30}\\$)$", pUser)) {
            return false;
        } else
            return true;
    }

    private void initOptions() {
        Option optionUser = OptionBuilder
                .withArgName(USER)
                .hasArg()
                .withDescription("username whose access to be evaluated on the ACE")
                .isRequired(true)
                .create(USER);

        Option optionACE = OptionBuilder
                .withArgName(ACE)
                .hasArg()
                .withDescription("ACE (Access Control Expression) Ex: 'u:mapr|u:root'")
                .isRequired(true)
                .create(ACE);


        Option optionCleanUp = OptionBuilder
                .withArgName(CLEAN)
                .hasArg()
                .withDescription("clean the temp file 'maprfs:///tmp/AceEvaluator-<ts>-<user>' (true)")
                .isRequired(false)
                .create(CLEAN);

        Option optionIsDb = OptionBuilder
                .hasArg()
                .withArgName("proxy")
                .withDescription("should the ace be checked using db semantics\n" +
                        "proxy - a proxy user to root/mapr")
                .isRequired(false)
                .create("db");

        inputOptions.addOption(optionUser);
        inputOptions.addOption(optionACE);
        inputOptions.addOption(optionCleanUp);
        inputOptions.addOption(optionIsDb);

        printOptions.addOption(optionACE);
        printOptions.addOption(optionUser);
        printOptions.addOption(optionIsDb);
    }

    private static void printHelp(String reasonIfError){
        new HelpFormatter().printHelp("checkACE"
                ,"checkAccess for the given user w.r.t the specified ace expression"
                , printOptions
                , reasonIfError
                , true);
    }
}
