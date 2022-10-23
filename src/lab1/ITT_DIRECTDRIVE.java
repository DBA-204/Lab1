
package lab1;

import Environment.Environment;
import ai.Choice;
import ai.DecisionSet;


public class ITT_DIRECTDRIVE extends ITT {
    
    @Override
    public void setup(){
        this.enableDeepLARVAMonitoring();
        super.setup();
        
        A = new DecisionSet();
        A.addChoice(new Choice("MOVE") );
        A.addChoice(new Choice("LEFT") );
        A.addChoice(new Choice("RIGHT") );

    }
    
    @Override
    public Status MyJoinSession() {
        // Añadimos un servicio
        this.DFAddMyServices(new String[]{"TYPE AT_ST"});
        // Nos uniremos a una sesión de trabajo
        outbox = session.createReply();
        outbox.setContent("Request join session " + sessionKey);
        this.LARVAsend(outbox);
        session = LARVAblockingReceive();
        if( !session.getContent().startsWith("Confirm") ){
            Error("Could not join session "+ sessionKey + " due " + 
                    session.getContent());
            return Status.CLOSEPROBLEM;
        }    
        
        this.openRemote();
        this.MyReadPerceptions();
        return Status.SOLVEPROBLEM;
    }   
    
    @Override
    public Status MySolveProblem(){
        // Si estoy en estado objetivo, he finalizado
        if( G(E) ){
            Message("Problem " + problem + " has been solved");
            return Status.CLOSEPROBLEM;
        }
        // Si el estado no es objetivo, y tampoco estoy vivo doy alerta
        if( !Ve(E) ){
            Alert("Sorry, the agent has crashed!");
            return Status.CLOSEPROBLEM;
        }
        
        // Elijo una de las acciones a ejecutar
        Choice a = this.Ag(E, A);
        if( a == null ){
            Alert("Sorry, no action possible");
            return Status.CLOSEPROBLEM;
        }
        
        Info("Try to execute " + a );
        this.MyExecuteAction(a.getName());
        // Leemos las percepciones para la próxima iteración
        this.MyReadPerceptions();
        return Status.SOLVEPROBLEM;
    }
    
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
    
    @Override
    protected double U(Environment E, Choice a ){
        return(goAhead(E,a));
    }
    
    @Override
    public String easyPrintPerceptions(){
        return super.easyPrintPerceptions() + "\n" + 
                this.Prioritize(E, A).toString();
    }
    
}
