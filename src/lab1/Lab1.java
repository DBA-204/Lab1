
package lab1;

import appboot.LARVABoot;
import static crypto.Keygen.getHexaKey;

public class Lab1 {
    
    public static void main(String[] args) {
        LARVABoot boot = new LARVABoot();
        boot.Boot("isg2.ugr.es", 1099); 
        boot.launchAgent("ITT "+getHexaKey(4), ITT.class);
        boot.WaitToShutDown();
    }
}
