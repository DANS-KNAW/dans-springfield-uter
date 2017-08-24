/* 
* DansDropboxThread.java
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Date;
import java.util.List;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Node;
import org.springfield.uter.dans.xml.Action;
import org.springfield.uter.dans.xml.ActionFS;

public class DansDropboxThread
extends Thread {
    private static Logger LOG = Logger.getLogger(DansDropboxThread.class);
    private static boolean running = false;
    private static String importFolder = "/springfield/inbox";
    private static int TIMEOUT = 300;
    private static int SLEEP = 60000;

    public DansDropboxThread() {
        LOG.setLevel(Level.DEBUG);
        if (!running) {
            running = true;
            this.start();
        }
    }

    @Override
    public void run() {
        try {
            while (running) {
                LOG.info("Running DansDropbox");
                File[] files = DansDropboxThread.getFiles(importFolder, "xml");
                if (files != null) {
                    LOG.debug("Found " + files.length + " xml files to process");
                    for (int i = 0; i < files.length; ++i) {
                        Date fileLastModifiedDate = new Date(files[i].lastModified());
                        Date currentDate = new Date();
                        if (currentDate.getTime() - fileLastModifiedDate.getTime() < (long)TIMEOUT) {
                            LOG.debug("File not yet long enough on server");
                            continue;
                        }
                        if (!files[i].isFile()) {
                        	LOG.debug("Expecting xml file instead of folder - "+files[i].getName());
                        	continue;
                        }
                        if (!files[i].canRead()) {
                            LOG.debug("Cannot read file - "+files[i].getName()+" - check permissions");
                            continue;
                        }
                        this.validateXml(files[i]);
                    }
                }
                Thread.sleep(SLEEP);
            }
            LOG.info("Stopped running DansDropbox");
        }
        catch (InterruptedException e) {
            LOG.error("Interrupted exception" + e);
            LOG.info("Trying to run again");
            this.run();
        }
    }

    private void validateXml(File xml) {
        String content = DansDropboxThread.readFile(xml);
        if (content == null) {
            LOG.error("Could not process empty file.");
            return;
        }
        Document easyXml = null;
        try {
            easyXml = DocumentHelper.parseText((String)content);
        }
        catch (DocumentException e) {
            LOG.error("Non valid xml found - "+xml.getName());
            LOG.error(e.toString());
            return;
        }
        List<Node> actions = easyXml.selectNodes("/actions/*");
        LOG.debug("Validating " + actions.size() + " actions");
        Action[] actionArray = new Action[actions.size()];
        int i = 0;
        for (Node node : actions) {
            Action action;
            actionArray[i] = action = new Action(node);
            ++i;
            if (action.isValid()) continue;
            LOG.debug("Non valid actions found");
            this.rejectFile(xml);
            return;
        }
        
        //clear as another validate is done per action to fill variables for process()
        ActionFS.instance().clear();
        
        for (int j = 0; j < actionArray.length; ++j) {
            actionArray[j].process();
        }
        
        ActionFS.instance().clear();
        LOG.info("Moving xml file " + xml.getName() + " to /springfield/processed");
        xml.renameTo(new File("/springfield/processed/" + xml.getName()));
    }

    private void rejectFile(File xml) {
        ActionFS.instance().clear();
    }

    private static File[] getFiles(String directory, final String extension) {
        File d = new File(directory);
        File[] array = d.listFiles(new FilenameFilter(){

            @Override
            public boolean accept(File dir, String name) {
                LOG.debug("Checking if we need to accept file " + name);
                if (name.equals(".") || name.equals("..")) {
                    return false;
                }
                if (!name.toLowerCase().endsWith(extension)) {
                    return false;
                }
                return true;
            }
        });
        return array;
    }

    private static String readFile(File input) {
        BufferedReader in = null;
        StringBuffer str = new StringBuffer("");
        try {
            int ch;
            in = new BufferedReader(new InputStreamReader((InputStream)new FileInputStream(input), "UTF8"));
            while ((ch = in.read()) != -1) {
                str.append((char)ch);
            }
            in.close();
        }
        catch (IOException e) {
            LOG.error("Could not read xml file." + e.toString());
            return null;
        }
        return str.toString();
    }

    public void stopTask() {
        running = false;
    }

}

