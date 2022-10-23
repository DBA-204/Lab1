
package lab1;

import Environment.Environment;
import ai.Choice;

public class ITT_BASICAVOID extends ITT_DIRECTDRIVE {
    
    @Override
    public void setup(){
        super.setup();
    }
       
    // Función para evitar un obstaculo
    public double goAvoid(Environment E, Choice a){
        // Si tengo que girar, priorizo el giro a la derecha primero
        if( a.getName().equals("RIGHT")){
            // Si es lo que quiero utilizar, le doy un valor menor
            return Choice.ANY_VALUE;
        }
        else{
            // Si lo quiero descartar, le doy el máximo valor
            return Choice.MAX_UTILITY;
        }
    }
    
    @Override
    protected double U(Environment E, Choice a ){
        // Comprobamos cuanto vale hacer una acción en un momento determinado
        if( E.isFreeFront() ){
            return(goAhead(E,a));
        }
        else{
            return goAvoid(E,a);
        }
    }
    
    
}
