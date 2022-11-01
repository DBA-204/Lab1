package lab1;

import Environment.Environment;
import agents.DEST;
import agents.DroidShip;
import agents.LARVAFirstAgent;
import ai.Choice;
import ai.DecisionSet;
import geometry.Point3D;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import java.util.ArrayList;
import tools.emojis;
import world.Perceptor;

public class ITT extends LARVAFirstAgent {

    // The same statuses but there is a new one: JOINSESSION
    protected enum Status {
        START, CHECKIN, CHECKOUT, OPENPROBLEM, CLOSEPROBLEM, JOINSESSION, SOLVEPROBLEM, EXIT
    }
    protected Status myStatus;
    protected String service = "PMANAGER", problem = "Endor.Sob2", // Dagobah.Apr1
            problemManager = "", content, sessionKey, sessionManager;
    protected String problems[], plan[], actions[];
    protected ACLMessage open, session;
    protected String[] contentTokens;
    protected String action = "", preplan = "";
    protected int indexplan = 0, myEnergy = 0, indexSensor = 0;
    protected boolean showPerceptions = false, useAlias = false;
    protected String cities[];
    protected Point3D positions[];
    protected String mission;
    
    // Booleano para controlar a que ciudad ir (si tiene ruta o no)
    protected boolean missionActive = true;
    
    //ArrayList para guardar el report que se mandará a la nave DEST
    protected ArrayList<String> reportList = new ArrayList<String>();
    protected String reportData = "";
    
    protected String sessionAlias;
    
    protected String muro, sigMuro;
    protected double distance, sigDistance;
    protected Point3D point, nextPoint;
    

    @Override
    public void setup() {
        super.setup();
        showPerceptions = false;
        logger.onTabular();
        myStatus = Status.START;
        this.setupEnvironment();
        actions = new String[]{
            "LEFT",
            "RIGHT",
            "MOVE",
            "EXIT"};
        
        A = new DecisionSet();
        A.addChoice(new Choice("MOVE"));
        A.addChoice(new Choice("LEFT"));
        A.addChoice(new Choice("RIGHT"));
        
        // Desactivamos el sequence diagrams
        this.deactivateSequenceDiagrams();
        
        this.enableDeepLARVAMonitoring();
    }
    
   

    // 99% Recycled from AgentLARVAFull
    @Override
    public void Execute() {
        Info("\n\n\nStatus: " + myStatus.name());
        switch (myStatus) {
            case START:
                myStatus = Status.CHECKIN;
                break;
            case CHECKIN:
                myStatus = MyCheckin();
                break;
            case OPENPROBLEM:
                myStatus = MyOpenProblem();
                break;
            case JOINSESSION: // This is new wrt HelloWorld
                myStatus = MyJoinSession();
                break;
            case SOLVEPROBLEM:
                myStatus = MySolveProblem();
                break;
            case CLOSEPROBLEM:
                myStatus = MyCloseProblem();
                break;
            case CHECKOUT:
                myStatus = MyCheckout();
                break;
            case EXIT:
            default:
                doExit();
                break;
        }
    }

    // 100% Reciclado de HelloWorld
    @Override
    public void takeDown() {
        Info("Taking down...");
        super.takeDown();
    }

    // 100% Reciclado de HelloWorld
    public Status MyCheckin() {
        Info("Loading passport and checking-in to LARVA");
        if (!doLARVACheckin()) {
            Error("Unable to checkin");
            return Status.EXIT;
        }
        return Status.OPENPROBLEM;
    }

    // 100% Reciclado de HelloWorld
    public Status MyCheckout() {
        this.doLARVACheckout();
        return Status.EXIT;
    }

    // 99% Reciclado de HelloWorld
    public Status MyOpenProblem() {
        if (this.DFGetAllProvidersOf(service).isEmpty()) {
            Error("Service PMANAGER is down");
            return Status.CHECKOUT;
        }
        problemManager = this.DFGetAllProvidersOf(service).get(0);
        Info("Found problem manager " + problemManager);
        if (problem == null) {
            return Status.CHECKOUT;
        }
        this.outbox = new ACLMessage();
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(problemManager, AID.ISLOCALNAME));
        
        sessionAlias = "JORDICONDEMOLINA";
        Info("Request open " + problem + " alias " + sessionAlias);
        outbox.setContent("Request open " + problem + " alias " + sessionAlias);
        this.LARVAsend(outbox);
        Info("Request opening problem " + problem + " to " + problemManager);
        open = LARVAblockingReceive();
        Info(problemManager + " says: " + open.getContent());
        content = open.getContent();
        contentTokens = content.split(" ");
        if (contentTokens[0].toUpperCase().equals("AGREE")) {
            sessionKey = contentTokens[4];
            session = LARVAblockingReceive();
            sessionManager = session.getSender().getLocalName();
            Info(sessionManager + " says: " + session.getContent());
            return Status.JOINSESSION; // This is the only change wrt HelloWorld
        } else {
            Error(content);
            return Status.CHECKOUT;
        }
    }
    
    public Status MyJoinSession() {
        this.resetAutoNAV();
        // Lo primero es consultar las ciudades
        Info("Querying CITIES");
        outbox = new ACLMessage();
        outbox.setSender(this.getAID());
        outbox.addReceiver(new AID(sessionManager, AID.ISLOCALNAME));
        outbox.setContent("Query CITIES session " + sessionKey);
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        getEnvironment().setExternalPerceptions(session.getContent());
        
        // Leemos las ciudades
        cities = this.getEnvironment().getCityList();
        
        // Pedimos join session
        this.DFAddMyServices(new String[]{"TYPE ITT"});
        outbox = session.createReply();
        outbox.setContent("Request join session " + sessionKey + " in GuildHouse");
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        if (!session.getContent().startsWith("Confirm")) {
            Error("Could not join session " + sessionKey + " due to " + session.getContent());
            return Status.CLOSEPROBLEM;
        }
        
        // Lanzamos los NPC
        Info("Preparing NPCs ...");
        this.doPrepareNPC(1,DEST.class);
        
        //Mostrar el droidship
        DroidShip.Debug();
        
        // Vemos las misiones y seleccionamos la primera        
        outbox.setContent("Query missions session " + sessionKey);
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        getEnvironment().setExternalPerceptions(session.getContent());
        
        this.MyReadPerceptions();
        mission = chooseMission();
        getEnvironment().setCurrentMission(mission);
        
        return Status.SOLVEPROBLEM;
    }
    
    //Metodo para reutilizar el next goal e is over
    public Status isProblemSolved(){
        E.getCurrentMission().nextGoal();
        missionActive = true;
        if(E.getCurrentMission().isOver()){
            return Status.CLOSEPROBLEM;
        }else{
            return Status.SOLVEPROBLEM;
        }             
    }

    // Complete autonomy
    public Status MySolveProblem() {
        
        // Vemos la goal que tenemos que hacer
        String goal = E.getCurrentGoal();
        
        if( goal.startsWith("MOVEIN") ){           
            String[] ciudad = goal.split(" ");
            for(int i = 0; i < ciudad.length; i++){
                Info(ciudad[i]);
            }
            
            if( missionActive ){
                this.myAssistedNavigation(ciudad[1]);
                missionActive = false;
            }
            
            if(G(E)){
                Message("Ha llegado a " + ciudad[1]);
                reportData += ";" + ciudad[1]; //REPORT;<city> ...
                return isProblemSolved();
            }
            if(!Ve(E)){
                Alert("Ostia tio que no lo he enchufao");
                return Status.CLOSEPROBLEM;
            }

            Choice action = this.Ag(E, A);
            if(action == null){
                Alert("No sabe que hacer");
                return Status.CLOSEPROBLEM;
            }

            this.MyExecuteAction(action.getName());
            this.MyReadPerceptions();
            return Status.SOLVEPROBLEM;
        } else if( goal.startsWith("LIST") ){
            // Listamos las personas, SITH en este caso
            String[] datos = goal.split(" ");
            Info("tipo -> " + datos[1]);
            return doQueryPeople(datos[1]);
            //return Status.CLOSEPROBLEM;
        } else if(goal.startsWith("REPORT")){
            reportData += ";";
            Info("REPORT" + reportData);
            //Mandar el REPORT a un agente de tipo DEST
            
            ArrayList<String> npcs = this.DFGetAllProvidersOf("TYPE DEST");
            Info("INFO NPCS: " + npcs.get(0));
            //Creamos el mensaje
            outbox = new ACLMessage();
            outbox.setSender(this.getAID());
            outbox.addReceiver(new AID(npcs.get(0), false));
            
            //Si solo hago el createReply, dice que es bad report
            //outbox = session.createReply();
            outbox.setContent("REPORT" + reportData);
            this.LARVAsend(outbox);
            session = LARVAblockingReceive();

            getEnvironment().setExternalPerceptions(session.getContent());
            
            return isProblemSolved();
        }
        
        if( E.getCurrentMission().isOver() ){
            return Status.CLOSEPROBLEM;
        }
        
        // Pasamos a la siguiente mision
        //getEnvironment().getCurrentMission().nextGoal();
        return Status.SOLVEPROBLEM;
    }

    // Just mark an X Y position as our next target. No more
    // Marcamos la ciudad objetivo
    protected Status myAssistedNavigation(String goal) {
        Info("Requesting course in " + goal);
        outbox = session.createReply();
        outbox.setContent("Request course in " + goal + " Session " + sessionKey);
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        getEnvironment().setExternalPerceptions(session.getContent());
        return Status.SOLVEPROBLEM;
    }

    // 100% New method to execute an action
    protected boolean MyExecuteAction(String action) {
        Info("Executing action " + action);
        outbox = session.createReply();
        // Remember to include sessionID in all communications
        outbox.setContent("Request execute " + action + " session " + sessionKey);
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        if (!session.getContent().startsWith("Inform")) {
            Error("Unable to execute action " + action + " due to " + session.getContent());
            return false;
        }
        return true;
    }

    // Read perceptions and send them directly to the Environment instance,
    // so we can query any items of sensors and added-value information
    protected boolean MyReadPerceptions() {
        Info("Reading perceptions");
        outbox = session.createReply();
        outbox.setContent("Query sensors session " + sessionKey);
        this.LARVAsend(outbox);
        this.myEnergy++;
        session = this.LARVAblockingReceive();
        if (session.getContent().startsWith("Failure")) {
            Error("Unable to read perceptions due to " + session.getContent());
            return false;
        }
        getEnvironment().setExternalPerceptions(session.getContent());
      //  Info(this.easyPrintPerceptions());
        return true;
    }

    // 99% recycled from HelloWorld
    public Status MyCloseProblem() {
        outbox = open.createReply();
        outbox.setContent("Cancel session " + sessionKey);
        Info("Closing problem " + problem + ", session " + sessionKey);
        this.LARVAsend(outbox);
        session = LARVAblockingReceive();
        Info(problemManager + " says: " + session.getContent());
        
        // Destruir NPCs
        this.doDestroyNPC();
        
        return Status.CHECKOUT;
    }

    // A new method just to show the information of sensors in console
    public String easyPrintPerceptions() {
        String res;
        int matrix[][];
        if (!logger.isEcho()) {
            return "";
        }
        if (getEnvironment() == null) {
            Error("Environment is unacessible, please setupEnvironment() first");
            return "";
        }
        if (!showPerceptions) {
            return "";
        }
        res = "\n\nReading of sensors\n";
        if (getEnvironment().getName() == null) {
            res += emojis.WARNING + " UNKNOWN AGENT";
            return res;
        } else {
            res += emojis.ROBOT + " " + getEnvironment().getName();
        }
        res += "\n";
        res += String.format("%10s: %05d W %05d W %05d W\n", "ENERGY",
                getEnvironment().getEnergy(), getEnvironment().getEnergyburnt(), myEnergy);
        res += String.format("%10s: %15s\n", "POSITION", getEnvironment().getGPS().toString());
//        res += "PAYLOAD "+getEnvironment().getPayload()+" m"+"\n";
        res += String.format("%10s: %05d m\n", "X", getEnvironment().getGPS().getXInt())
                + String.format("%10s: %05d m\n", "Y", getEnvironment().getGPS().getYInt())
                + String.format("%10s: %05d m\n", "Z", getEnvironment().getGPS().getZInt())
                + String.format("%10s: %05d m\n", "MAXLEVEL", getEnvironment().getMaxlevel())
                + String.format("%10s: %05d m\n", "MAXSLOPE", getEnvironment().getMaxslope());
        res += String.format("%10s: %05d m\n", "GROUND", getEnvironment().getGround());
        res += String.format("%10s: %05d º\n", "COMPASS", getEnvironment().getCompass());
        if (getEnvironment().getTarget() == null) {
            res += String.format("%10s: " + "!", "TARGET");
        } else {
            res += String.format("%10s: %05.2f m\n", "DISTANCE", getEnvironment().getDistance());
            res += String.format("%10s: %05.2f º\n", "ABS ALPHA", getEnvironment().getAngular());
            res += String.format("%10s: %05.2f º\n", "REL ALPHA", getEnvironment().getRelativeAngular());
        }
//        res += "\nVISUAL ABSOLUTE\n";
//        matrix = getEnvironment().getAbsoluteVisual();
//        for (int y = 0; y < matrix[0].length; y++) {
//            for (int x = 0; x < matrix.length; x++) {
//                res += printValue(matrix[x][y]);
//            }
//            res += "\n";
//        }
//        for (int x = 0; x < matrix.length; x++) {
//            if (x != matrix.length / 2) {
//                res += "----";
//            } else {
//                res += "[  ]-";
//            }
//        }
        res += "\nVISUAL RELATIVE\n";
        matrix = getEnvironment().getRelativeVisual();
        for (int y = 0; y < matrix[0].length; y++) {
            for (int x = 0; x < matrix.length; x++) {
                res += printValue(matrix[x][y]);
            }
            res += "\n";
        }
        for (int x = 0; x < matrix.length; x++) {
            if (x != matrix.length / 2) {
                res += "----";
            } else {
                res += "[  ]-";
            }
        }
//        res += "VISUAL POLAR\n";
//        matrix = getEnvironment().getPolarVisual();
//        for (int y = 0; y < matrix[0].length; y++) {
//            for (int x = 0; x < matrix.length; x++) {
//                res += printValue(matrix[x][y]);
//            }
//            res += "\n";
//        }
//        res += "\n";
        res += "LIDAR RELATIVE\n";
        matrix = getEnvironment().getRelativeLidar();
        for (int y = 0; y < matrix[0].length; y++) {
            for (int x = 0; x < matrix.length; x++) {
                res += printValue(matrix[x][y]);
            }
            res += "\n";
        }
        for (int x = 0; x < matrix.length; x++) {
            if (x != matrix.length / 2) {
                res += "----";
            } else {
                res += "-^^-";
            }
        }
        res += "\n";
        return res;
    }

    protected String printValue(int v) {
        if (v == Perceptor.NULLREAD) {
            return "XXX ";
        } else {
            return String.format("%03d ", v);
        }
    }

    protected String printValue(double v) {
        if (v == Perceptor.NULLREAD) {
            return "XXX ";
        } else {
            return String.format("%05.2f ", v);
        }
    }
    
    /***********************************************************************/
    
    // Primera implementación autonav
    
    public double goAhead(Environment E, Choice a){
        // El giro lo hacemos en base a la distancia en que me quedaría si 
        // me muevo
        if( a.getName().equals("MOVE")){
            return U(S(E,a));
        }
        else{ // LEFT o RIGHT
            return U(S(E,a),new Choice("MOVE"));
        }        
    }
    /*************************************************************************/
    
    
    /*protected String chooseMission(){
        Info("Choosing a mission");
        String m = "";
        if( getEnvironment().getAllMissions().length == 1 ){
            m = getEnvironment().getAllMissions()[0];
        }
        else{
            m = this.inputSelect("Please choose a mission", getEnvironment().getAllMissions(), "");
        }
        Info("Selected mission " + m);
        return m;        
    }*/
    
    protected Status doQueryPeople( String type ){
        Info("Querying people " + type);
        outbox = session.createReply();
        outbox.setContent("Query " + type.toUpperCase() + " session " + sessionKey);
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        getEnvironment().setExternalPerceptions(session.getContent());
        Message("Found " + getEnvironment().getPeople().length + " " + type +
                " in " + getEnvironment().getCurrentCity());
        
        //Para completar la mision de REPORT debe devolver un string con 
        //todas las listas que ha recogido en las ciudades visitadas
        reportData += " " + type.toLowerCase() + " " 
                + getEnvironment().getPeople().length;
        
        return isProblemSolved();
    }
    
    @Override
    protected Choice Ag(Environment E, DecisionSet A) {
        if (G(E)) {
            return null;
        } else if (A.isEmpty()) {
            return null;
        } else {
            A = Prioritize(E, A);
            muro = sigMuro;
            distance = sigDistance;
            point = nextPoint;
            return A.BestChoice();
        }
    }
    
    
    // We need to refactor again Utility funciont to split it into three cases
    // Two of them as in the previous version, and the new one to implement
    // The left-wall heuristic (always turn to the right in front of an obstacle.
    @Override
    protected double U(Environment E, Choice a) {
        if (muro.equals("RIGHT")) {
            return goFollowWallRight(E, a);
        }if (muro.equals("LEFT")) {
            return goFollowWallLeft(E, a);
        } else if (!E.isFreeFront()) {
            return goAvoid(E, a);
        } else {
            return goAhead(E, a);
        }
    }

    
    // Refactor goAvoid, so that the first time we get to an obstacle, we remember 
    // its position for further use
 
    public double goAvoid(Environment E, Choice a) {
        if (a.getName().equals("LEFT")) {
            sigMuro = "RIGHT";
            sigDistance = E.getDistance();
            nextPoint = E.getGPS();
            return Choice.ANY_VALUE;
        }
        if (a.getName().equals("RIGHT")) {
            sigMuro = "LEFT";
            sigDistance = E.getDistance();
            nextPoint = E.getGPS();
            return Choice.ANY_VALUE;
        }
        return Choice.MAX_UTILITY;
    }
    
        // This is a new function, pleas read the other below and, then, continue reading this
    // This is a Utility function to follow a wall until is very end. I keep
    // always stuck to the wall. If the wall turns, I turn with it too.
    // When I get to a better positoin (closer to the goal) that the one I memorized
    // at the begining of the obstacle, then, stop surrounding and go back towards the goal.
    public double goFollowWallLeft(Environment E, Choice a) {
        if (E.isFreeFrontLeft()) {
            return goTurnOnWallLeft(E, a);
        } else if (E.isTargetFrontRight()
                && E.isFreeFrontRight()
                && E.getDistance() < point.planeDistanceTo(E.getTarget())) {
            return goStopWallLeft(E, a);
        } else if (E.isFreeFront()) {
            return goKeepOnWall(E, a);
        } else {
            return goRevolveWallLeft(E, a);
        }

    }
    
    public double goFollowWallRight(Environment E, Choice a) {
        if (E.isFreeFrontRight()) {
            return goTurnOnWallRight(E, a);
        } else if (E.isTargetFrontLeft()
                && E.isFreeFrontLeft()
                && E.getDistance() < point.planeDistanceTo(E.getTarget())) {
            return goStopWallRight(E, a);
        } else if (E.isFreeFront()) {
            return goKeepOnWall(E, a);
        } else {
            return goRevolveWallRight(E, a);
        }

    }

    // If I am still surrounding an obstacle and the front is free, just move to the front
    public double goKeepOnWall(Environment E, Choice a) {
        if (a.getName().equals("MOVE")) {
            return Choice.ANY_VALUE;
        }
        return Choice.MAX_UTILITY;
    }

    // If I am sourronding an obstacle and the obstacle turns, I turn with it
    public double goTurnOnWallLeft(Environment E, Choice a) {
        if (a.getName().equals("LEFT")) {
            return Choice.ANY_VALUE;
        }
        return Choice.MAX_UTILITY;

    }
    public double goTurnOnWallRight(Environment E, Choice a) {
        if (a.getName().equals("RIGHT")) {
            return Choice.ANY_VALUE;
        }
        return Choice.MAX_UTILITY;

    }

    // The same as before but the other turn
    public double goRevolveWallLeft(Environment E, Choice a) {
        if (a.getName().equals("RIGHT")) {
            return Choice.ANY_VALUE;
        }
        return Choice.MAX_UTILITY;
    }
    
    public double goRevolveWallRight(Environment E, Choice a) {
        if (a.getName().equals("LEFT")) {
            return Choice.ANY_VALUE;
        }
        return Choice.MAX_UTILITY;
    }

    // Very important. I I reacha a better position than the one memorized
    // at the begining of the obstacle, then I stop surronding and go back towards the goal
    public double goStopWallLeft(Environment E, Choice a) {
        if (a.getName().equals("RIGHT")) {
            this.resetAutoNAV();
            return Choice.ANY_VALUE;
        }
        return Choice.MAX_UTILITY;
    }
    
    public double goStopWallRight(Environment E, Choice a) {
        if (a.getName().equals("LEFT")) {
            this.resetAutoNAV();
            return Choice.ANY_VALUE;
        }
        return Choice.MAX_UTILITY;
    }
    
    public void resetAutoNAV() {
        sigMuro = muro = "NONE";
        sigDistance = distance = Choice.MAX_UTILITY;
        nextPoint = point = null;
    }

}
