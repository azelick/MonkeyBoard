/*
 * MonkeyBoard Copyright (C) 2011 Oliver Bartley
 * 
 * MonkeyBoard is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * MonkeyBoard is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with MonkeyBoard.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.brtly.monkeyboard;

import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JButton;
import javax.swing.DefaultListModel;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JOptionPane;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.ImageIcon;
import javax.swing.SwingWorker;
import javax.swing.Timer;

import com.android.chimpchat.ChimpChat;
import com.android.chimpchat.core.IChimpDevice;
import com.android.chimpchat.core.IChimpImage;
import com.android.chimpchat.core.TouchPressType;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import javax.swing.border.BevelBorder;
import javax.swing.text.Document;
import javax.swing.text.SimpleAttributeSet;

import java.awt.Cursor;
import java.awt.Event;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.JMenuBar;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JTextPane;
import java.awt.Dimension;
import java.awt.Component;
import javax.swing.SwingConstants;
import java.awt.Font;
import javax.swing.JScrollPane;
import java.awt.event.InputEvent;
import java.awt.Color;
import javax.swing.JCheckBoxMenuItem;

public class MonkeyBoard {
	private DefaultListModel listModel = new DefaultListModel();
	private JList listView = null;
	private JButton btnMonkeyBoard = null;
	private JTextPane textConsole = null;
	private ArrayList<JMenuItem> deviceMenuItems = new ArrayList<JMenuItem>(); //stores a reference to all menu items that need to be
																				//disabled when there isn't a device connected.
	private JCheckBoxMenuItem chckbxmntmShowKeyEvents = null; // option menu
	
	private Timer tmrRefresh = null;
	
	JFrame frmMonkeyboard;

    private ChimpChat mChimpChat;
    private IChimpDevice mDevice; 
    private String connectedDeviceId = null;
    private String desktopPath;
   
    private static String androidSdkPath = null;
    private static String androidSdkAdbPath = null;
    private static String androidSdkEmulatorPath = null;
    
    private static final String MOTD = "MonkeyBoard v0.1";
    private static final String CONSOLE_SEPARATOR = "---------\n";
    private static final String HELP_TEXT = ">>> USAGE\n" +
    				CONSOLE_SEPARATOR +
    				"Special key combos:\n" +
    				"Home          Ctrl+H\n" +
    	    		"Menu          Ctrl+M\n" +
    	    		"Search        Ctrl+S\n" +
    				"Camera        Ctrl+C (Ctrl+F3)\n" +
		    		"Volume up     Ctrl+= (Ctrl+F5)\n" +
		    		"Volume down   Ctrl+- (Ctrl+F6)\n" +
		    		"Dpad center   Ctrl+Enter\n" +
		    		CONSOLE_SEPARATOR +
		    		"adb commands executed from the 'Execute adb Command...' option\n" +
		    		"follow the pattern:\n" +
		    		"	adb -s $(device id) $(command)\n" +
		    		"where $(device id) represents the currently connected device's serial number\n" +
		    		"and $(command) represents the command entered into the input dialog.\n" +
		    		CONSOLE_SEPARATOR;
		  
    private static final long TIMEOUT = 5000;
    private static final int REFRESH_DELAY = 1000;
    // ddms default filename == "device-2011-12-23-160423.png"
    public static final String TIMESTAMP_FORMAT = "yyyy-MM-dd-HHmmss";
    
    // lookup table to translate from Java keycodes to Android
    private Map<Integer, String> keyCodeMap = new TreeMap<Integer, String>();
    
    // Set to track which android keycodes are currently in a down state,
    // so that they can be quickly matched with a keyup in the event of a focus lost event
    private Set<String> keysPressed = new HashSet<String>();
    
	/**
	 * Create the application.
	 */
	public MonkeyBoard() {
		// init UI
		initialize(); // this method is only for GUI elements manipulated on the Eclipse windowBuilder Design tab
		initializeKeyCodeMap(); //static map used to translate Java keycodes to Android
		
		// initialize sdk path variables
	    initSdkPath();
	    
		// create the adb backend
		TreeMap<String, String> options = new TreeMap<String, String>();
        options.put("backend", "adb");
        options.put("adbLocation", androidSdkAdbPath);
		mChimpChat = ChimpChat.getInstance(options);
		desktopPath = System.getProperty("user.home") + "/Desktop";


		
	    	
		refreshDeviceList();
		
		// create the timer that refreshes the device list
		@SuppressWarnings("serial")
		AbstractAction timerAction = new AbstractAction() {
		    public void actionPerformed(ActionEvent e) {
		    	refreshDeviceList();
		    }
		};
		tmrRefresh = new Timer(REFRESH_DELAY, timerAction);
		tmrRefresh.start();
	}
	
	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					MonkeyBoard window = new MonkeyBoard();
					window.frmMonkeyboard.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	/**
	 * wrapper function to allow resetting of the SDK path from the GUI menu
	 * works by deleting the config file and calling the initSdkPath usually only run at launch
	 */
	private void resetSdkPath() {
		
		disconnectFromDevice();
		
		try {
			File confFile = null;
			String confPath = System.getenv("HOME") + "/.monkeyboard";
			confFile = new File(confPath);
			confFile.delete();
		} catch (Exception e) {
			toConsole("there was an error removing ~/.monkeyboard");
		}
		
		initSdkPath();
		if (JOptionPane.showOptionDialog (this.frmMonkeyboard,	    	
			    "A restart is required for these changes to take effect. Quit now?",
			    "SDK Path Updated",
			    JOptionPane.YES_NO_OPTION,
			    JOptionPane.WARNING_MESSAGE, null, null, null) == JOptionPane.YES_OPTION) {
			disconnectFromDevice();
			System.exit(0);
		}
		
		
		
	}
	
	/**
	 * initialize android sdk, adb and emulator path variables
	 * this method expects androidSdkPath, androidSdkAdbPath and androidSdkEmulatorPath to be initialized to null at the class level
	 */
	private void initSdkPath() {
		File confFile = null;
		String confPath = System.getenv("HOME") + "/.monkeyboard";
		JFrame frm = new JFrame();
		try {
			// try loading from config file
			confFile = new File(confPath);
			Scanner confScanner = new Scanner(confFile);
			// Scanner is a simple iterator
			androidSdkPath = confScanner.nextLine(); // THIS ASSUMES THERE IS ONLY ONE LINE CONTAINING THE SDK PATH!
		} catch (Exception e) {
			// show a dialog and directory chooser
			JOptionPane.showMessageDialog(frm,	    	
				    "Select the root directory of your SDK installation.",
				    "MonkeyBoard Setup",
				    JOptionPane.INFORMATION_MESSAGE);
			
			JFileChooser fileChooser = new JFileChooser();
			// TODO: make this use FileDialog if on OS X?
			fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			fileChooser.setDialogTitle("Choose Android SDK location");
			fileChooser.showOpenDialog(null);
			try {
				androidSdkPath = fileChooser.getSelectedFile().toString();
			} catch ( NullPointerException npe) {
	    		// can't find!
				// show a dialog
				JOptionPane.showMessageDialog(frm,	    	
					    "Cannot locate Android SDK. Please restart MonkeyBoard to update your SDK Path settings.",
					    "SDK Path Error",
					    JOptionPane.ERROR_MESSAGE);
				// delete the config file to force a new path selection next launch
				confFile.delete();
				System.exit(1);
			}
		}
			
		// set us up the bomb, set up the executable paths
		if (androidSdkPath.endsWith("/")) // then strip trailing slash
    		androidSdkPath = androidSdkPath.substring(0, androidSdkPath.length() - 1);
    	androidSdkAdbPath = androidSdkPath + "/platform-tools/adb";
    	androidSdkEmulatorPath = androidSdkPath + "/tools/emulator";
    	
    	// check and see if the path is correct,
    	// make sure we can find adb
    	File adbBin = new File(androidSdkAdbPath);
    	if ( ! adbBin.exists() ) {
    		// can't find!
			// show a dialog
			JOptionPane.showMessageDialog(frmMonkeyboard,	    	
				    "Cannot locate Android SDK. Please restart MonkeyBoard to update your SDK Path settings.",
				    "SDK Path Error",
				    JOptionPane.ERROR_MESSAGE);
			// delete the config file to force a new path selection next launch
			confFile.delete();
			System.exit(1);
    	} else {
    		// save the SDK path for next time
    		try {
            	BufferedWriter out = new BufferedWriter(new FileWriter(confPath));
    	    	out.write(androidSdkPath);
    			out.close();
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    	}
    			   
    	// let it be known!
    	toConsole("Using Android SDK: " + androidSdkPath);
	}
	
	/**
	 *  Append a String to the text in the console and force scrolling to the end of the doc
	 *  basically a Log
	 */
	public void toConsole(String arg0) {

		if ( arg0.contains("KEYCODE") && ( !chckbxmntmShowKeyEvents.isSelected())) {
			// exit if it's a keycode event and the option is unchecked
			return;
		}
		
		try {
			// get document from console and append arg0
			Document d = textConsole.getDocument();
			SimpleAttributeSet attributes = new SimpleAttributeSet();
			d.insertString(d.getLength(), '\n' + arg0, attributes);
			textConsole.invalidate(); // force update of console to reduce forced scrolling issues
			// force scrolling to end of output
			textConsole.scrollRectToVisible(new Rectangle(0, textConsole.getHeight() + 32, 1, 1));
		} catch (Exception e) {
			System.err.println("Error instering:" + arg0);
			e.printStackTrace();
		}
	}
	
	/**
	 * Capture output from `adb devices`, parses it and returns data in a Map in the form of
	 * deviceId:key::status:value
	 * @return
	 */
	private Map<String, String> getAdbStatus() {
		// check that the sdk path is set
		// don't nag, because this is run on a timer
		if (androidSdkPath == null) return new HashMap<String, String>();
		
		String cmd = androidSdkAdbPath + " devices";
		Runtime run = Runtime.getRuntime();
		Process pr = null;
		Map<String, String> rv = new HashMap<String, String>();
		
		// execute cmd
		try {
			pr = run.exec(cmd);
			pr.waitFor();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// parse output
		BufferedReader buf = new BufferedReader(new InputStreamReader(pr.getInputStream()));
		String line = "";
		try {
			while ((line=buf.readLine())!=null) {
				if ( ! (line.startsWith("List") || line.length() <= 1) ) {
					String[] s = line.split("\\s+"); //it's a tab separated list, dude!
					rv.put(s[0], s[1]); // add deviceId as key, status as value to Map
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return rv;
	}

	/**
	 * execute an adb command via
	 * adb -s connectedDeviceId cmd
	 * useful for uninstalling packages, etc
	 */
	private void execAdbCommand(final String args) {
		// threaded subprocess
		SwingWorker <Object, Void> worker = new SwingWorker<Object, Void>() {
		    @Override
		    public Object doInBackground() {
				Runtime run = Runtime.getRuntime();
				Process pr = null;
				String cmd = null;
				
				if (args == null) return null;
				if (connectedDeviceId == null) 
					// this is really only an option so restarting adb can use this function
					cmd = androidSdkAdbPath + " " + args;
				else 
					cmd = androidSdkAdbPath + " -s " + connectedDeviceId + " " + args;
				toConsole(">>> " + cmd);
				
				// execute cmd
				try {
					pr = run.exec(cmd);
					pr.waitFor();
				} catch (Exception e) {
					e.printStackTrace();
				}
				
				// parse output
				BufferedReader buf = new BufferedReader(new InputStreamReader(pr.getInputStream()));
				String line = "";
				try {
					while ((line=buf.readLine())!=null) {
						toConsole(".   " + line);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
				return null;
		    }
		};
		worker.execute();
	}
	
	/**
	 * the only reason this one is used exclusively for install is because
	 * the default process runner used in execAdbCommand treats whitespace as delimiting arguments, regardless of
	 * escape chars or quotes. Because some filepaths use spaces O_o, we need to pass the args as an array instead
	 * @param apkPath
	 */
	private void execAdbInstallCommand(final String apkPath) {
		SwingWorker <Object, Void> worker = new SwingWorker<Object, Void>() {
		    @Override
		    public Object doInBackground() {
				Runtime run = Runtime.getRuntime();
				Process pr = null;
				
				if (apkPath == null) return null;
				if (connectedDeviceId == null) return null;
				
				String[] cmd = {androidSdkAdbPath, "-s", connectedDeviceId, "install", apkPath};
		
				toConsole(">>> " + androidSdkAdbPath + " -s " + connectedDeviceId + " install " + '"' + apkPath + '"');
				
				// execute cmd
				try {
					pr = run.exec(cmd);
					pr.waitFor();
				} catch (Exception e) {
					e.printStackTrace();
				}
				
				// parse output
				BufferedReader buf = new BufferedReader(new InputStreamReader(pr.getInputStream()));
				String line = "";
				try {
					while ((line=buf.readLine())!=null) {
						toConsole(".   " + line);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
				return null;
		    }
		};
		worker.execute();
	}

	/**
	 * Given the name of an AVD, launch an emulator instance of it in a worker thread
	 * @param name
	 */
	private void startAvd(final String name) {
		// check that the sdk path is set
		if (androidSdkPath == null) {
			toConsole("Cannot locate Android SDK!");
			return;
		} else if (name == null) {
			return;
		}
		
		SwingWorker <Object, Void> worker = new SwingWorker<Object, Void>() {
		    @Override
		    public Object doInBackground() {
				Runtime run = Runtime.getRuntime();
				Process pr = null;
				
				String cmd = androidSdkEmulatorPath + " -avd " + name;

				toConsole(">>> " + cmd);
				
				// execute cmd
				try {
					pr = run.exec(cmd);
					pr.waitFor();
				} catch (Exception e) {
					e.printStackTrace();
				}
				return null;
		    }
		};
		// now run the thread
		worker.execute();
	}
	
	/**
	 * refreshes data in listModel to reflect the current status of devices connected to adb
	 */
	private void refreshDeviceList() {
		Map<String, String> adb = getAdbStatus();
		Iterator<Entry<String, String>> adbDevices = adb.entrySet().iterator();
		Boolean foundConnectedDevice = false;
		String selectedElement = null;
		int selectedIndex = -1;
		int i = 0;
		
		// save a reference to the currently selected item
		// so we can reselect it after the list is rebuilt
		try {
			selectedElement = listModel.getElementAt(listView.getSelectedIndex()).toString();
		} catch (Exception e) {
			selectedElement = "";
		}
		
		// iterate over the items in adb
		listModel.clear();
		while (adbDevices.hasNext()) {
			Entry<String, String> dev =  (Entry<String, String>) adbDevices.next();

			String devId = dev.getKey().trim();
			String devStatus = dev.getValue().trim();
			Map <String, String> elem = new HashMap<String, String>();

			// build list element
			if ( devId.equals(connectedDeviceId) ) {
				// some special treatment for the device matching connectedDeviceId
				devStatus = "connected";
				foundConnectedDevice = true;
			}
			elem.put("deviceId", devId);
			elem.put("deviceStatus", devStatus);
			listModel.addElement(elem);
			
			if (selectedElement.contains(devId))
				// we found a match to the previously selected device
				// save an index to it
				selectedIndex = i;
			i++;
		}
		
		if ( ! foundConnectedDevice) {
			// a deviceId matching connectedDevcieId was not found, reset connection
			disconnectFromDevice();
		}
		
		// if a match was found in the above loop, then this will be true
		if (selectedIndex > -1)
			listView.setSelectedIndex(selectedIndex);
		
		// now we scrub the list for stuff that isn't in the deviceList anymore
		if ( listModel.getSize() > 0) {
			for (i = 0; i == listModel.getSize(); i++) {
				if (adb.keySet().contains(listModel.getElementAt(i)))
					listModel.remove(i);
			}
		}
	}
	
	/**
	 * define a background thread to connect to the device selected in the list
	 */
	@SuppressWarnings("unchecked")
	private void connectToDevice() {
		// define the worker
		SwingWorker <Object, Void> worker = new SwingWorker<Object, Void>() {
		    @Override
		    public Object doInBackground() {
			HashMap<String, String> v;
			String deviceId = null;
			
			// get the device id from the selected list element
			try {
				v = (HashMap<String, String>) listView.getSelectedValue();	
				deviceId = v.get("deviceId");
			} catch (NullPointerException ex) {
				// an npe is fine, it just means there's nothing selected
				disconnectFromDevice();
				refreshDeviceList();
				return null;
			}
	
			// if the device is already selected, disconnect instead
			if (deviceId.equals(connectedDeviceId)) {
				disconnectFromDevice();
				refreshDeviceList();
				return null;
			}
			toConsole("connecting to device: " + deviceId);
			//get a connection to the device
			try {
		        mDevice = mChimpChat.waitForConnection(TIMEOUT, deviceId);
		        if ( mDevice == null ) throw new RuntimeException("Couldn't connect.");
		        mDevice.wake();
		        connectedDeviceId = deviceId;
		        toConsole("connected.");
		        setDeviceMenuItemsEnabled(true);
			} catch (Exception e) {
				e.printStackTrace();
				disconnectFromDevice();
	        	toConsole("failed to connect.");    
			}
			refreshDeviceList();
		  	return null;
		    }
		};
		// now run the thread
		worker.execute();
	}
	
	/**
	 * handles disposing of connection object and disabling menu items that require a connected device
	 */
	private void disconnectFromDevice() {
		if (connectedDeviceId != null) {
			toConsole("diconnected from device:" + connectedDeviceId);
			connectedDeviceId = null;
		}
		if (mDevice != null) {
			mDevice.dispose();
			mDevice = null;
		}
		setDeviceMenuItemsEnabled(false);
		// keep the focus request within the window, other wise focus will jump from
		// input dialogs as well.
		textConsole.requestFocusInWindow();
	}
	
	/**
	 * iterate over items in deviceMenuItems and call .setEnabled(b)
	 * this is used when connecting/disconnecting to a device
	 * @param b
	 */
	private void setDeviceMenuItemsEnabled(Boolean b) {
		Iterator<JMenuItem> iter = deviceMenuItems.iterator();		
		while (iter.hasNext()) {
			iter.next().setEnabled(b);
		}
	}
	
	/**
	 * display a list of device properties in the console
	 */
	private void getDeviceProperties() {
		String[] props = {"build.board",
		     	"build.brand",
		     	"build.device",
		     	"build.fingerprint",
		     	"build.host",
		     	"build.ID",
		     	"build.model",
		     	"build.product",
		     	"build.tags",
		     	"build.type",
		     	"build.user",
		     	"build.CPU_ABI",
		     	"build.manufacturer",
		     	"build.version.incremental",
		     	"build.version.release",
		     	"build.version.codename",
		     	"display.width",
		     	"display.height",
		     	"display.density"};
		toConsole("Device properties for " + connectedDeviceId);
		try {
			for (int i = 0; i < props.length; i++ ) {
				// pad property name with spaces to create a pretty table
				toConsole(String.format("%1$-" + 30 + "s", props[i]) + mDevice.getProperty(props[i]));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Handler for all screenshot functions. If a filename is passed, a screenshot will be saved there
	 * if "" or null is passed, the na default filename will be generated and the file will be saved to the user's desktop
	 * @param filename
	 */
	private void screenshotHandler(String filename) {
		// if there was no filename passed, give it a default and save to desktop
		if ((filename == "") || (filename == null)) {
			// ddms default filename = "device-2011-12-23-160423.png"
			Calendar cal = Calendar.getInstance();
		    SimpleDateFormat sdf = new SimpleDateFormat(TIMESTAMP_FORMAT);
		    filename = desktopPath + "/device-" + sdf.format(cal.getTime()) + ".png";	    			
		}
		// now do the saving...
		try {
			toConsole("Saving snapshot to " + filename);
			IChimpImage img = mDevice.takeSnapshot();
			img.writeToFile(filename, "png");
		} catch (Exception e) {
			toConsole("there was an error saving " + filename);
			e.printStackTrace();
		}
	}
	
	/**
	 * dumps logcat of the connected device to the filename specified
	 * if no filename is specified, dumps file to user's desktop
	 * @param filename
	 */
	private void logcatHandler(String filename) {
		// I don't think this one needs to be threaded
		FileWriter fWriter = null;
		BufferedWriter writer = null; 
		Runtime rt = Runtime.getRuntime();
		String s;
		
		// if there was no filename passed, give it a default and save to desktop
		if ((filename == "") || (filename == null)) {
			// ddms default filename = "device-2011-12-23-160423.png"
			Calendar cal = Calendar.getInstance();
		    SimpleDateFormat sdf = new SimpleDateFormat(TIMESTAMP_FORMAT);
		    filename = desktopPath + "/device-" + sdf.format(cal.getTime()) + ".log";	    			
		}
		
		// use an array anytime we handle filenames
		String[] cmd = {androidSdkAdbPath, "-s", connectedDeviceId, "logcat", "-d"};

		toConsole(">>> " + androidSdkAdbPath + " -s " + connectedDeviceId + " logcat -d > " + '"' + filename + '"');
		
		try {
			// initialize file objects
			fWriter = new FileWriter(filename);
			writer = new BufferedWriter(fWriter);
			
			// execute the command
			Process proc = rt.exec(cmd);

			// get output streams
			BufferedReader stdInput = new BufferedReader(new 
	             InputStreamReader(proc.getInputStream()));

	        BufferedReader stdError = new BufferedReader(new 
	             InputStreamReader(proc.getErrorStream()));

	        // read the output from the output streams
	        while ((s = stdInput.readLine()) != null) {
	        	// write the line to file
	            writer.write(s);
	  		  	writer.newLine();
	        }
	        
	        // close file stream
	        writer.close();
	        
	        // read any errors from the attempted command
	        //System.out.println("Here is the standard error of the command (if any):\n");
	        while ((s = stdError.readLine()) != null) {
	            System.out.println(s);
	        }
		}catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Handles keyPress and keyRelease events to be sent to connected device
	 * 
	 * @param keyCode
	 * @param modifiers
	 * @param type
	 */
	private void keyEventHandler(int keyCode, int modifiers, TouchPressType type) {
		String code = null;
		String stype = (type == TouchPressType.DOWN)?"DOWN":"UP";
		//Boolean isShift = ((modifiers & 0x01) == 1);
		Boolean isCtrl = ((modifiers & 0x02) == 2);
		Boolean isMeta = ((modifiers & 0x04) == 4);
		//Boolean isAlt = ((modifiers & 0x08) == 8);	
		
		// manually map some special ctrl+keyevents
		// TODO: make this not so brittle. incorporate this into a keymap?
		switch (keyCode) {
			case KeyEvent.VK_ENTER:
				// if the special mapping is already pressed, it's a keyup
				// the reason we don't care about isCtrl is it's possible the user
				// can release ctrl before the key in question, and then a release event will never be sent
				if ((isCtrl && (type == TouchPressType.DOWN)) || 
						(keysPressed.contains("KEYCODE_DPAD_CENTER")))
					code = "KEYCODE_DPAD_CENTER"; 
				break;
			
			// emulator parity
			case KeyEvent.VK_F3:
				if ((isCtrl && (type == TouchPressType.DOWN)) || 
						(keysPressed.contains("KEYCODE_CAMERA")))
					code = "KEYCODE_CAMERA"; 
				break;				
			case KeyEvent.VK_F5:
				if ((isCtrl && (type == TouchPressType.DOWN)) || 
						(keysPressed.contains("KEYCODE_VOLUME_UP")))
					code = "KEYCODE_VOLUME_UP"; 
				break;
			case KeyEvent.VK_F6:
				if ((isCtrl && (type == TouchPressType.DOWN)) || 
						(keysPressed.contains("KEYCODE_VOLUME_DOWN")))
					code = "KEYCODE_VOLUME_DOWN"; 
				break;
			
			// these ones make more sense than the emulator defaults
			case KeyEvent.VK_M:
				if ((isCtrl && (type == TouchPressType.DOWN)) || 
						(keysPressed.contains("KEYCODE_MENU")))
					code = "KEYCODE_MENU"; 
				break;
			case KeyEvent.VK_S:
				if ((isCtrl && (type == TouchPressType.DOWN)) || 
						(keysPressed.contains("KEYCODE_SEARCH")))
					code = "KEYCODE_SEARCH"; 
				break;
			case KeyEvent.VK_H:
				if ((isCtrl && (type == TouchPressType.DOWN)) || 
						(keysPressed.contains("KEYCODE_HOME")))
					code = "KEYCODE_HOME"; 
				break;
			case KeyEvent.VK_P:
				if ((isCtrl && (type == TouchPressType.DOWN)) || 
						(keysPressed.contains("KEYCODE_POWER")))
					code = "KEYCODE_POWER"; 
				break;
			case KeyEvent.VK_C:
				if ((isCtrl && (type == TouchPressType.DOWN)) || 
						(keysPressed.contains("KEYCODE_CAMERA")))
					code = "KEYCODE_CAMERA"; 
				break;
			case KeyEvent.VK_MINUS:
				if ((isCtrl && (type == TouchPressType.DOWN)) || 
						(keysPressed.contains("KEYCODE_VOLUME_DOWN")))
					code = "KEYCODE_VOLUME_DOWN"; 
				break;
			case KeyEvent.VK_EQUALS:
				if ((isCtrl && (type == TouchPressType.DOWN)) || 
						(keysPressed.contains("KEYCODE_VOLUME_UP")))
					code = "KEYCODE_VOLUME_UP"; 
				break;
		}
		
		// if code is still null, then do a regular lookup in the map
		if ( code == null ) code = keyCodeMap.get(keyCode);
		
		// still null? nothing to do here
		if ( code == null ) return;
		
		// ignore all meta + keydown (Such as Command + S)
		// TODO: also ignore keyups if they're not in keysPressed
		if (isMeta && (type == TouchPressType.DOWN)) return;	
		
		// if it's a keydown and there's already a reference in keysPressed, don't send another keydown 
		if ((! code.contains("DPAD")) &&
				(keysPressed.contains(code)) && // only allow spamming trackball commands
				(type == TouchPressType.DOWN))
			return;
		
		// now we focus on sending it to the device, log it
		toConsole("[" + Integer.toString(keyCode) + ":" + Integer.toString(modifiers) + "] " + code + " " + stype);
		
		// make sure the state of the key is properly stored
		switch(type) {
		case DOWN:keysPressed.add(code); break;
		case UP:keysPressed.remove(code); break;
		}
		
		// actually send the key event if we're connected to a device
		if (connectedDeviceId != null)  {
			mDevice.press(code, type);
		}
	}

	/**
	 * iterate over items in keysPressed to return all keys to an unpressed state
	 * this is useful as a deadfall switch to quickly return all keys issued a down command
	 * a matching up command in the event of lost focus
	 */
	private void resetKeysPressed() {
		Iterator<String> iter = keysPressed.iterator();
		String code;
	    while (iter.hasNext()) {
	    	code = iter.next();
	    	toConsole("[-:-] " + code + " UP");
			if (connectedDeviceId != null) {
				mDevice.press(code, TouchPressType.UP);
			} 
	    }
	    keysPressed.clear();
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frmMonkeyboard = new JFrame();
		frmMonkeyboard.setMinimumSize(new Dimension(512, 360));
		frmMonkeyboard.setTitle("MonkeyBoard");
		frmMonkeyboard.setBounds(100, 100, 512, 360);
		frmMonkeyboard.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		// moved JList declaration to class-level declarartions
		listView = new JList(listModel);
		listView.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				// double click to connect
				if(e.getClickCount() == 2) {
					int index = listView.locationToIndex(e.getPoint());
					//Object item = listModel.getElementAt(index);;
					listView.ensureIndexIsVisible(index);
					connectToDevice();
				}
			}
		});
		listView.setAlignmentY(Component.TOP_ALIGNMENT);
		listView.setAlignmentX(Component.LEFT_ALIGNMENT);
		listView.setBorder(new BevelBorder(BevelBorder.LOWERED, null, null, null, null));
		listView.setCellRenderer(new DeviceListRenderer());
		JButton btnRefresh = new JButton("Refresh");
		btnRefresh.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent arg0) {
				refreshDeviceList();
			}
		});
		
		JButton btnConnect = new JButton("Connect");
		btnConnect.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				connectToDevice();
			}
		});		
		btnMonkeyBoard = new JButton("");
		btnMonkeyBoard.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent arg0) {
				// if the button has focus, then clicking it will give focus to something else, like a toggle switch.
				if ( btnMonkeyBoard.hasFocus() ) {
					textConsole.requestFocus();
				}
			}
		});
		btnMonkeyBoard.setHorizontalTextPosition(SwingConstants.CENTER);
		btnMonkeyBoard.setFocusTraversalKeysEnabled(false);
		btnMonkeyBoard.setAlignmentX(Component.CENTER_ALIGNMENT);
		btnMonkeyBoard.setIconTextGap(0);
		btnMonkeyBoard.setPressedIcon(null);
		btnMonkeyBoard.setSelectedIcon(new ImageIcon(MonkeyBoard.class.getResource("/res/keyboard_on.png")));
		btnMonkeyBoard.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btnMonkeyBoard.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				keyEventHandler(e.getKeyCode(), e.getModifiers(), TouchPressType.DOWN);
				//btnMonkeyBoard.
			}
			@Override
			public void keyReleased(KeyEvent e) {
				keyEventHandler(e.getKeyCode(), e.getModifiers(), TouchPressType.UP);
			}
		});
		btnMonkeyBoard.addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent arg0) {
				if (chckbxmntmShowKeyEvents.isSelected() &&
						( ! (connectedDeviceId == null))) 
					toConsole("key events released");
				btnMonkeyBoard.setSelected(false);
				resetKeysPressed();
			}
			@Override
			public void focusGained(FocusEvent arg0) {
				// if we try to connect here, we run into a wierd hang.
				// don't try to be convenient an add connectToDevice() here!
				if (connectedDeviceId == null) {
					listView.requestFocus();
					toConsole("no device connected!");
				} else {
					// begin trapping key events
					if (chckbxmntmShowKeyEvents.isSelected()) 
						toConsole("key events trapped");
					btnMonkeyBoard.setSelected(true);
				}
			}
		});	
		btnMonkeyBoard.setBorder(null);
		btnMonkeyBoard.setIcon(new ImageIcon(MonkeyBoard.class.getResource("/res/keyboard.png")));
		
		JScrollPane consoleScrollPane = new JScrollPane();

		GroupLayout groupLayout = new GroupLayout(frmMonkeyboard.getContentPane());
		groupLayout.setHorizontalGroup(
			groupLayout.createParallelGroup(Alignment.LEADING)
				.addGroup(groupLayout.createSequentialGroup()
					.addContainerGap()
					.addGroup(groupLayout.createParallelGroup(Alignment.LEADING)
						.addGroup(groupLayout.createSequentialGroup()
							.addComponent(consoleScrollPane, GroupLayout.DEFAULT_SIZE, 438, Short.MAX_VALUE)
							.addContainerGap())
						.addGroup(groupLayout.createSequentialGroup()
							.addGroup(groupLayout.createParallelGroup(Alignment.LEADING)
								.addGroup(groupLayout.createSequentialGroup()
									.addComponent(btnRefresh)
									.addPreferredGap(ComponentPlacement.RELATED)
									.addComponent(btnConnect)
									.addGap(0, 0, Short.MAX_VALUE))
								.addComponent(listView, GroupLayout.DEFAULT_SIZE, 193, Short.MAX_VALUE))
							.addGap(3)
							.addComponent(btnMonkeyBoard, GroupLayout.PREFERRED_SIZE, 246, Short.MAX_VALUE)
							.addGap(2))))
		);
		groupLayout.setVerticalGroup(
			groupLayout.createParallelGroup(Alignment.LEADING)
				.addGroup(groupLayout.createSequentialGroup()
					.addContainerGap()
					.addGroup(groupLayout.createParallelGroup(Alignment.TRAILING, false)
						.addGroup(groupLayout.createSequentialGroup()
							.addComponent(listView, GroupLayout.PREFERRED_SIZE, 244, GroupLayout.PREFERRED_SIZE)
							.addPreferredGap(ComponentPlacement.RELATED)
							.addGroup(groupLayout.createParallelGroup(Alignment.BASELINE)
								.addComponent(btnConnect)
								.addComponent(btnRefresh)))
						.addComponent(btnMonkeyBoard, GroupLayout.PREFERRED_SIZE, 279, GroupLayout.PREFERRED_SIZE))
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(consoleScrollPane, GroupLayout.DEFAULT_SIZE, 92, Short.MAX_VALUE)
					.addContainerGap())
		);
		
		textConsole = new JTextPane();
		textConsole.setText(MOTD);
		//textConsole.setText("ready");
		textConsole.setForeground(new Color(166, 199, 58)); //android green
		textConsole.setFont(new Font("Monospaced", Font.PLAIN, 15));
		textConsole.setEditable(false);
		textConsole.setBackground(new Color(0, 0, 0));
		consoleScrollPane.setViewportView(textConsole);
		frmMonkeyboard.getContentPane().setLayout(groupLayout);
		
		JMenuBar menuBar = new JMenuBar();
		menuBar.setBorder(null);
		frmMonkeyboard.setJMenuBar(menuBar);
		
		JMenu mnMain = new JMenu("Device");
		menuBar.add(mnMain);
		
		JMenuItem mntmStopAdbServer = new JMenuItem("Kill adb Server");
		mntmStopAdbServer.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				disconnectFromDevice();
				execAdbCommand("kill-server");
				// can't issue a start server here, because it's threaded.
				// nest just wait till refresh device list comes around again
			}
		});
		
		JMenuItem mntmConnectToDevice = new JMenuItem("Connect To Device");
		mntmConnectToDevice.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				connectToDevice();
			}
		});
		mntmConnectToDevice.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.META_MASK));
		mnMain.add(mntmConnectToDevice);
		mntmStopAdbServer.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_UNDEFINED, 0));
		mnMain.add(mntmStopAdbServer);		
		
		JSeparator separator = new JSeparator();
		mnMain.add(separator);
		
		JMenuItem mntmLaunchEmulator = new JMenuItem("Launch Emulator...");
		mntmLaunchEmulator.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				Component source = (Component) arg0.getSource();
				String avd = JOptionPane.showInputDialog(source,
		                "Enter the name of the AVD you want to lauch:",
		                "Launch Emulator",
		                JOptionPane.QUESTION_MESSAGE);
				startAvd(avd);
			}
		});
		mntmLaunchEmulator.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.META_MASK));
		mnMain.add(mntmLaunchEmulator);
		
		mnMain.add(new JSeparator());
		
		JMenuItem mntmInstallapk = new JMenuItem("Install *.apk Package...");
		mntmInstallapk.setEnabled(false);
		mntmInstallapk.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.META_MASK));
		mntmInstallapk.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				// Show a file chooser dialog and
				// issue an adb install command if the filepath
				// return contains '.apk'
				// TODO: convert to portable JFileChooser
				FileDialog fd = new FileDialog(frmMonkeyboard, "Select an .apk package to install");
				fd.show();
				String apk = fd.getDirectory() + fd.getFile();
				if (apk.contains(".apk"))
					execAdbInstallCommand(apk);
				
				
			}
		});
		
		mnMain.add(mntmInstallapk);
		deviceMenuItems.add(mntmInstallapk);
		
		JMenuItem mntmExecuteShellCommand = new JMenuItem("Run adb Command...");
		mntmExecuteShellCommand.setEnabled(false);
		mntmExecuteShellCommand.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.META_MASK));
		mntmExecuteShellCommand.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				Component source = (Component) arg0.getSource();
				String cmd = JOptionPane.showInputDialog(source,
						"adb -s " + connectedDeviceId, // this could potentially be a NullPointer, but the menu items are disabled when connectedDeviceId == null
						"Run adb Command",
						JOptionPane.INFORMATION_MESSAGE);
				execAdbCommand(cmd);
			}
		});
		mnMain.add(mntmExecuteShellCommand);
		deviceMenuItems.add(mntmExecuteShellCommand);
		
		JMenuItem mntmGetDeviceProperties = new JMenuItem("Get Device Info");
		mntmGetDeviceProperties.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				getDeviceProperties();
			}
		});
		mntmGetDeviceProperties.setEnabled(false);
		mntmGetDeviceProperties.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.META_MASK));
		mnMain.add(mntmGetDeviceProperties);
		deviceMenuItems.add(mntmGetDeviceProperties);
		
		mnMain.add(new JSeparator());
		
		JMenuItem mntmSaveScreenshot = new JMenuItem("Save Screenshot");
		mntmSaveScreenshot.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				screenshotHandler(null);
			}
		});
		mntmSaveScreenshot.setEnabled(false);
		mntmSaveScreenshot.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, Event.META_MASK));
		mnMain.add(mntmSaveScreenshot);
		deviceMenuItems.add(mntmSaveScreenshot);
		
		JMenuItem mntmSaveLogcat = new JMenuItem("Save Logcat");
		mntmSaveLogcat.setEnabled(false);
		mntmSaveLogcat.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.META_MASK));
		mntmSaveLogcat.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				logcatHandler("");
			}
		});
		deviceMenuItems.add(mntmSaveLogcat);
		
		mnMain.add(mntmSaveLogcat);
		
		JMenu mnOptions = new JMenu("Options");
		menuBar.add(mnOptions);
		
		// class level declaration
		chckbxmntmShowKeyEvents = new JCheckBoxMenuItem("Show Key Log in Console");
		chckbxmntmShowKeyEvents.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				if (chckbxmntmShowKeyEvents.isSelected())
					toConsole("[-:-] KEYLOG ON");
				else
					toConsole("[-:-] KEYLOG OFF");
			}
		});
		chckbxmntmShowKeyEvents.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.META_MASK));
		chckbxmntmShowKeyEvents.setSelected(true);
		mnOptions.add(chckbxmntmShowKeyEvents);
		
		JMenuItem mntmResetSdkPath = new JMenuItem("Set Android SDK Path...");
		mntmResetSdkPath.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
			resetSdkPath();
			}
		});
		
		JMenuItem mntmConsoleHelp = new JMenuItem("Send Help Text to Console");
		mntmConsoleHelp.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_SLASH, InputEvent.META_MASK));
		mntmConsoleHelp.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				toConsole(HELP_TEXT);
			}
		});
		mnOptions.add(mntmConsoleHelp);
		
		JMenuItem mntmClearConsole = new JMenuItem("Flush Scrollback Buffer");
		mntmClearConsole.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				textConsole.setText(MOTD);
			}
		});
		mnOptions.add(mntmClearConsole);
		mnOptions.add(mntmResetSdkPath);
	}
	
	public void initializeKeyCodeMap() {
		// modifiers
		keyCodeMap.put(KeyEvent.VK_SHIFT, "KEYCODE_SHIFT_LEFT");
		//keyCodeMap.put(KeyEvent.VK_CONTROL, "KEYCODE_CTRL_LEFT");					
		keyCodeMap.put(KeyEvent.VK_ALT, "KEYCODE_ALT_LEFT");
	
		// alphanumeric
		keyCodeMap.put(KeyEvent.VK_A, "KEYCODE_A");
		keyCodeMap.put(KeyEvent.VK_B, "KEYCODE_B");
		keyCodeMap.put(KeyEvent.VK_C, "KEYCODE_C");
		keyCodeMap.put(KeyEvent.VK_D, "KEYCODE_D");
		keyCodeMap.put(KeyEvent.VK_E, "KEYCODE_E");
		keyCodeMap.put(KeyEvent.VK_F, "KEYCODE_F");
		keyCodeMap.put(KeyEvent.VK_G, "KEYCODE_G");
		keyCodeMap.put(KeyEvent.VK_H, "KEYCODE_H");
		keyCodeMap.put(KeyEvent.VK_I, "KEYCODE_I");
		keyCodeMap.put(KeyEvent.VK_J, "KEYCODE_J");
		keyCodeMap.put(KeyEvent.VK_K, "KEYCODE_K");
		keyCodeMap.put(KeyEvent.VK_L, "KEYCODE_L");
		keyCodeMap.put(KeyEvent.VK_M, "KEYCODE_M");
		keyCodeMap.put(KeyEvent.VK_N, "KEYCODE_N");
		keyCodeMap.put(KeyEvent.VK_O, "KEYCODE_O");
		keyCodeMap.put(KeyEvent.VK_P, "KEYCODE_P");
		keyCodeMap.put(KeyEvent.VK_Q, "KEYCODE_Q");
		keyCodeMap.put(KeyEvent.VK_R, "KEYCODE_R");
		keyCodeMap.put(KeyEvent.VK_S, "KEYCODE_S");
		keyCodeMap.put(KeyEvent.VK_T, "KEYCODE_T");
		keyCodeMap.put(KeyEvent.VK_U, "KEYCODE_U");
		keyCodeMap.put(KeyEvent.VK_V, "KEYCODE_V");
		keyCodeMap.put(KeyEvent.VK_W, "KEYCODE_W");
		keyCodeMap.put(KeyEvent.VK_X, "KEYCODE_X");
		keyCodeMap.put(KeyEvent.VK_Y, "KEYCODE_Y");
		keyCodeMap.put(KeyEvent.VK_Z, "KEYCODE_Z");
		keyCodeMap.put(KeyEvent.VK_0, "KEYCODE_0");
		keyCodeMap.put(KeyEvent.VK_1, "KEYCODE_1");
		keyCodeMap.put(KeyEvent.VK_2, "KEYCODE_2");
		keyCodeMap.put(KeyEvent.VK_3, "KEYCODE_3");
		keyCodeMap.put(KeyEvent.VK_4, "KEYCODE_4");
		keyCodeMap.put(KeyEvent.VK_5, "KEYCODE_5");
		keyCodeMap.put(KeyEvent.VK_6, "KEYCODE_6");
		keyCodeMap.put(KeyEvent.VK_7, "KEYCODE_7");
		keyCodeMap.put(KeyEvent.VK_8, "KEYCODE_8");
		keyCodeMap.put(KeyEvent.VK_9, "KEYCODE_9");
		
		// dpad
		keyCodeMap.put(KeyEvent.VK_UP, "KEYCODE_DPAD_UP");
		keyCodeMap.put(KeyEvent.VK_DOWN, "KEYCODE_DPAD_DOWN");
		keyCodeMap.put(KeyEvent.VK_LEFT, "KEYCODE_DPAD_LEFT");
		keyCodeMap.put(KeyEvent.VK_RIGHT, "KEYCODE_DPAD_RIGHT");
		
		keyCodeMap.put(KeyEvent.VK_HOME, "KEYCODE_HOME");
		keyCodeMap.put(KeyEvent.VK_END, "KEYCODE_END");
		keyCodeMap.put(KeyEvent.VK_PAGE_UP, "KEYCODE_PAGE_UP");
		keyCodeMap.put(KeyEvent.VK_PAGE_DOWN, "KEYCODE_PAGE_DOWN");
		keyCodeMap.put(KeyEvent.VK_ESCAPE, "KEYCODE_BACK");
		
		// parity with android emulator
		keyCodeMap.put(KeyEvent.VK_F3, "KEYCODE_CALL");
		keyCodeMap.put(KeyEvent.VK_F4, "KEYCODE_ENDCALL");
		keyCodeMap.put(KeyEvent.VK_F5, "KEYCODE_SEARCH");
		keyCodeMap.put(KeyEvent.VK_F7, "KEYCODE_POWER");		
		
		// errata
		keyCodeMap.put(KeyEvent.VK_CLEAR, "KEYCODE_CLEAR");
		keyCodeMap.put(KeyEvent.VK_COMMA, "KEYCODE_COMMA");
		keyCodeMap.put(KeyEvent.VK_PERIOD, "KEYCODE_PERIOD");
		keyCodeMap.put(KeyEvent.VK_TAB, "KEYCODE_TAB");
		keyCodeMap.put(KeyEvent.VK_SPACE, "KEYCODE_SPACE");
		keyCodeMap.put(KeyEvent.VK_ENTER, "KEYCODE_ENTER");
		keyCodeMap.put(KeyEvent.VK_DELETE, "KEYCODE_DEL");
		keyCodeMap.put(KeyEvent.VK_BACK_SPACE, "KEYCODE_DEL");
		keyCodeMap.put(KeyEvent.VK_BACK_QUOTE, "KEYCODE_GRAVE");
		keyCodeMap.put(KeyEvent.VK_MINUS, "KEYCODE_MINUS");
		keyCodeMap.put(KeyEvent.VK_EQUALS, "KEYCODE_EQUALS");
		keyCodeMap.put(KeyEvent.VK_OPEN_BRACKET, "KEYCODE_LEFT_BRACKET");
		keyCodeMap.put(KeyEvent.VK_CLOSE_BRACKET, "KEYCODE_RIGHT_BRACKET");
		keyCodeMap.put(KeyEvent.VK_BACK_SLASH, "KEYCODE_BACKSLASH");
		keyCodeMap.put(KeyEvent.VK_SEMICOLON, "KEYCODE_SEMICOLON");
		keyCodeMap.put(KeyEvent.VK_SLASH, "KEYCODE_SLASH");
		keyCodeMap.put(KeyEvent.VK_AT, "KEYCODE_AT");
		keyCodeMap.put(KeyEvent.VK_PLUS, "KEYCODE_PLUS");

	}
}
