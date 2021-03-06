/* 
* UterContextListener.java
* 
* Copyright (c) 2015 Noterik B.V.
* 
* This file is part of Uter, related to the Noterik Springfield project.
*
* Uter is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* Uter is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with Uter.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.springfield.uter;

import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.springfield.uter.homer.LazyHomer;


public class UterContextListener implements ServletContextListener {

	private static LazyHomer lh = null; 
	private static DropboxCheckupThread dbox = null; 
	private static FixProvidersThread fp = null; 
	private static DansDropboxThread dansbox = null;
	
	public void contextInitialized(ServletContextEvent event) {
		System.out.println("UTER: context initialized");
		ServletContext servletContext = event.getServletContext();
 		LazyHomer lh = new LazyHomer();
		lh.init(servletContext.getRealPath("/"));

		//Make sure to start the dropbox after a minute so we are sure
		//the rest of the cluster is also up and running
		System.out.println("UTER: Waiting 1 minute before starting");
		Timer timer = new Timer();
		timer.schedule(new TimerTask() {
		    
		    @Override
		    public void run() {
			System.out.println("UTER: starting dropbox thread");
			dansbox = new DansDropboxThread();
		    }
		}, 60 * 1000);
	}
	
	public void contextDestroyed(ServletContextEvent event) {
		System.out.println("UTER: context destroyed");
		if(dbox!=null) {
			try {
				dbox.stopTask();
				dbox.interrupt();
				dbox.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				System.out.println("UTER: context destroyed: " + e);
			}
		}
		if(fp!=null) {

			try {
				fp.stopTask();
				fp.interrupt();
				fp.join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				System.out.println("UTER: context destroyed: " + e);
			}
		}
		
		if (dansbox != null) {
            try {
                dansbox.stopTask();
                dansbox.interrupt();
                dansbox.join();
            }
            catch (InterruptedException e) {
                System.out.println("UTER: context destroyed: " + e);
            }
        }
		
		lh.destroy();
		// stop timer
	//	timer.cancel();
		
		// destroy global config
		// GlobalConfig.instance().destroy();
	}
}
