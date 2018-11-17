/**
 *  SmarterHome
 *
 *  Copyright 2017 Jake Tebbett (jebbett)
 *  I would also like to give credit to all members for the SmartThings community, some of which have helped directly.
 *	others their code has been an inspiration or I have used snippets of their code to support this project where the licence has supported.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 * 
 * VERSION CONTROL
 *
 *	V0.0.1 - 20/01/17 - Initial Peek (Informal Release)
 *	V0.0.2 - 22/01/17 - Update to open doors messaging and handling
 *	v0.0.3 - 14/08/17 - Bug fix for delayed notifications & added color temp drop down
 *	v0.0.4 - 28/08/17 - Bug fix for leaving doors and windows open
 *	
 */
def version() {	return "v0.0.3" }

definition(
	name: "SmarterHome${parent ? " - Child" : ""}",
	namespace: "jebbett",
	author: "Jake Tebbett",
	description: "SmarterHome - Full home automation",
	singleInstance: true,
	parent: parent ? "jebbett.SmarterHome" : null,
	category: "Convenience",
	iconUrl: getIcon("home"),
	iconX2Url: getIcon("home"),
	iconX3Url: getIcon("home")
)

preferences {
// Standard
	page name: "pageMain"
    page name: "pageMainChild"
    page name: "pageMainParent"
    page name: "pageChangeName"
    page name: "pageAdvanced"
    
	page name: "pageSettings"
    page name: "pageModules"
    page name: "pageStatus"
    page name: "pageSecurity"
    page name: "pageWeather"
    page name: "pageMainControl"
    page name: "controlTrigger"
    
// Manual run
    page name: "manualTrigger"
    
// Scenes
    page name: "pageModeScene"

// Security
    page name: "pageSecArming"
    page name: "pageSecFireFlood"
    page name: "pageSecVacation"
    page name: "pageSecDoorOpen"
    
// TimeZones
    page name: "pageTimeZones"
    page name: "showTimeZoneDay"
    
// Zones
    page name: "pageZOccupied"
    page name: "pageZTemperature"
    
//Notifications
    page name: "pageNWhen"
    page name: "pageNDev"
    page name: "pageNMessaging"
    page name: "pageNLights"
    page name: "pageNAlarm"
    page name: "pageColourFlash"
// Virtual Device
	page name: "pageVirtDev"
    page name: "pageDevAdd"
}

def installed() {
    log.info "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.info "Updated with settings: ${settings}"
    unschedule()
    initialize()
}

def uninstalled() {
	if(!parent) sendLocationEvent(name: "appLink", value: "del" , isStateChange: true, descriptionText: "appLink Delete Event", data: [app: "$app.name"])
}

def initialize() {

	unsubscribe()
    
    if(parent){
    // CHILDAPP ACTIONS
        app.updateLabel("[${modType}] ${appName}")

        switch (settings.modType) {
			case "MultiMedia":
            	subscribe(mPlayerDT, "status", mPlayerDTCommand)
            break
            
            case "Notification":
            	subscribe(nMotion, "motion.active", nNotificationEvent)
                subscribe(nContact, "contact.closed", nNotificationEvent)
                subscribe(triggerSwitch, "switch.on", executeNotification)
                subscribe(location, "routineExecuted", routineChanged)
                stopNIP()
            break
            
            case "People":
            	subscribe(pPresence, "presence", pPresenceEvent)
                subscribe(pSwitch, "switch", pPresenceEvent)
                if(!state.present){state.present = false}
			break
            
            case "Scene":
            	subscribe(triggerSwitch, "switch", executeScene)
                subscribe(location, "routineExecuted", routineChanged)
            break    
			
            case "Zone":
            	subscribe(zMotion, "motion", zMotionDetected)
                subscribe(zContact, "contact", zMotionDetected)
                state.zMotionAlive = false
            break
            
            case "Security":
            	///// STUFF TO GO HERE
            break
            
		}

    }else{
    //PARENT APP ACTIONS
    //Notification subsriptions
        subscribe(app, repeatLastNotification)
        subscribe(location, "routineExecuted", routineChanged)
    //Security Subscriptions
    	subscribe(secDoorContact, "contact", secDoorHandler)
    	
    //Keep alive regular checks   
        subscribe(location, "sunset", keepAlive)
    	subscribe(location, "sunrise", keepAlive)
        keepAlive()
    //App Link
    	sendChildAppLink()
        subscribe(location, "$app.name", appLinkExecute)
        subscribe(location, "appLink", appLinkHandler)
    //States
    	if(!state.openDoors) state.openDoors = ""
    }
}


/*****************************************************************/
/** Main Pages
/*****************************************************************/

// Main
def pageMain() {
	parent ? pageMainChild() : (remoteDefault ? pageMainControl() : pageMainParent())
}

// Parent
private pageMainParent() {
	dynamicPage(name: "pageMainParent", title: "", install: true, uninstall: false) {
		section() {
        	href "pageMainControl", title: "Remote Control", image: getIcon("remo"), required: false
        	href "pageModules", title: "Modules", image: getIcon("apps"), required: false
            href "pageSecurity", title: "Security", image: getIcon("sec"), required: false
		}
        section(){
        	href "pageStatus", title: "Status", image: getIcon("info"), required: false
        	href "pageSettings", title: "Settings", image: getIcon("cp"), required: false
            paragraph "(►) Play button on SmartApps screen will replay last notification" 
    	}
	}
}

// Parent
private pageMainControl() {
	dynamicPage(name: "pageMainControl", title: "", install: remoteDefault, uninstall: false) {
		section() {
        	remoteList().each { childId, childName ->
       			href(name: "controlTrigger", title:"", description: childName, params: [cid: childId], state: "complete", page: "controlTrigger", image: getIcon(childName.substring(1, 5)))
			}
        }
        section(){
        	href(name: "pageMainParent", title:"", description: "Main Menu", page: "pageMainParent", state: "complete", image: getIcon("home"))
    		input "remoteDefault", "bool", title: "Make This Page The App Default", defaultValue:false
            paragraph("Add Scenes and Notifications under the Settings section")
        }
	}
}

/*****************************************************************/
/** Modules
/*****************************************************************/

// Parent Modules
private pageModules(params) {
	dynamicPage(name: "pageModules", title: "Modules", install: false) {
        section() {app(name:"SmartChild", title:"Add Module..", appName: "SmarterHome", namespace: "jebbett", multiple: true, uninstall: false, image: getIcon("plus"))}
        section("About Modules") {paragraph("Modules do the things to make your life easier")}
        
	}
}


// Child
private pageMainChild() {

	dynamicPage(name: "pageMainChild", title: "", install: true) {
    	// HEADER
        section("Module Type and Name") {
			def modTypesList = ["MultiMedia", "Notification", "People", "Scene", "Security", "Zone"]
            if(!modType){
            	input "modType", "enum", title: "Module Type", description: "(Can't Change Once Set)", required: true, submitOnChange: true, options: modTypesList
            }else{
            	href "pageMainChild", title: "${modType}", description: "", state: "complete"
            }
            input "appName", type: "text", title: "", description: "Module Name", required:true
		}
        
        // CONTENT
        switch (settings.modType) {
        	case null:
        		section("Types Of Module") {
                    paragraph("Specify a module type:"+
                    "\n• ZONE – A room or area within your property that you want to monitor for an action."+
                    "\n• SCENE – Sets lights, switches and other items to a specified state based on time of day."+
                    "\n• PEOPLE – A person that you want to monitor and respond to their actions."+
                    "\n• NOTIFICATION – Alerts in the form of app notifications, SMS, light effects or Audio."+
                    "\n• MULTIMEDIA – Set scenes based on the state of a media or music player e.g. Plex."+
                    "\n• SECURITY – Security modes and how they operate") 
				}
			break
			case "Zone":			showZone()			;specialFooter()	;break
            case "Scene":			showScene()			;specialFooter()	;break
			case "People":	 		showPresence()							;break
            case "MultiMedia":		showMuiltiMedia()	;specialFooter()	;break
            case "Notification":	showNotification()	;specialFooter()	;break
            case "Security":		showSecZone()							;break
		}
	}
}


private pageChangeName() {

	dynamicPage(name: "pageChangeName", title: "", install: false, uninstall: false) {
    	section("WARNING"){
    		paragraph("Changing the name of the app will break any associations or triggers and you will need to manually re-add.\n\nOnly if you are sure then please update the below.", title: "WARNING", required: true, state: null)
    		input "appName", type: "text", title: "",description: "Module Name", required:true
    	}
    }
}
    
def specialFooter(){
// FOOTER        
	section() {
    	href(name: "pageAdvanced", title:"", description: "Triggers / Restrictions", page: "pageAdvanced", image: getIcon("rules"))
	}
}

private pageAdvanced() {

	dynamicPage(name: "pageAdvanced", title: "", install: false) {
    	section(){
        	href(name: "manualTrigger", title:"", description: "Manually Activate Module", page: "manualTrigger",  image: getIcon("play"))
        }
        switch (settings.modType) {
        	case "Scene":
			case "Notification":
        		section("External Triggers") {
            		input "triggerRoutine", "enum", title: "Trigger if this routine set", required: false, options: getRoutines()
                    input "triggerSwitch", "capability.switch", title:"If this switch turns on trigger the above scene", multiple: false, required: false
                    href name: "pageVirtDev", title: "Create Virtual Switch", description: "", page: "pageVirtDev", state: getChildDevice(app.id)?"complete":null, required: false
    			}    
        	break
		}
        section(title: "Restrictions") {
            input "disabled", "capability.switch", title:"Don't run if this switch is on", multiple: false, required: false
            input "disabledOff", "capability.switch", title:"Don't run if this switch is off", multiple: false, required: false
            input "activeMode", "enum", title: "Run Only In These Modes", required: true, multiple: true, defaultValue: "Any", options: getModeList()
            input "activeSecMode", "enum", title: "Run Only In These Security Modes", required: true, multiple: true, defaultValue: "Any", options: parent.getSecModeList()
		}
        
    }
}

def generateAppName() {
	if (parent) {return null}
	def apps = getChildApps()
	def i = 1
    
	while (true) {
		def name = "Un Named Module #$i"
		def found = false
		for (app in apps) {
        	if (app.appName == name) {found = true; break}}
			if (found) {i++; continue}
		return name
	}
    
}

/*****************************************************************/
/** Virtual Device Creation
/*****************************************************************/

def pageVirtDev(){
	dynamicPage(name: "pageVirtDev", title: "", install: false) {
    	section(){
        	input "newDevType", "enum", title: "Device Type", description: "", required: true, submitOnChange: true, options: ["smartthings:Momentary Button Tile","smartthings:On/Off Button Tile"]
        	href(name: "pageDevAdd", title:"Create Device", description: "", page: "pageDevAdd", required: false)
        }
    }    
}
def pageDevAdd() {
	dynamicPage(name: "pageDevAdd", title: "Device Details", install: false, uninstall: false) {
    	section() {
            def existingDevice = getChildDevice(app.id)
            if(!existingDevice){
                def newDev = addChildDevice(settings.newDevType.split(":")[0], settings.newDevType.split(":")[1], app.id, null, [name: settings.appName, label: settings.appName])
                paragraph "Virtual Device Created!"
            }else{
            	paragraph "Virtual Device Already Linked To This App"
        	}
		}
	}
}

/*****************************************************************/
/** Time Modes
/*****************************************************************/

// TIME MODE PAGE
def pageTimeZones(params) {
	dynamicPage(name: "pageTimeZones", title: "Time Modes", install: false, uninstall: false) {
		section(){
        	input "tUseSTMode", "bool", title: "Use SmartThings Mode Instead Of Dynamic Time Mode Below", defaultValue: false, submitOnChange: true, required: false
            input "tSetSTMode", "bool", title: "Push SmarterHome Dynamic Mode To ST Every 15 Minutes", defaultValue: false, submitOnChange: true, required: false
        }
        
        if(!settings.tUseSTMode){
	        section(){
    	        def x = 2
        		for (int i = 1; i < x; i++) {
            		if(settings."dayOfWeek$i" != null){
            			href(name: "showTimeZoneDay$i", title:"", description: settings."dayOfWeek$i", params: [i: "$i"], page: "showTimeZoneDay", state: "complete", image: getIcon("calendar"))
            			x++
	                }else{
    	            	href(name: "showTimeZoneDay", title:"", description: "Add New Time Band", params: [i: "$i"], page: "showTimeZoneDay", image: getIcon("calendar"))
        	   		}
				}
        	}
        }
        section() {
			paragraph( "Here you can specify a Mode to be returned at different times to allow different things to happen around your house."+
            "\n\nMode names are taken from the SmartThings list of Modes. To add new modes you now need to login to the IDE > Locations > Your location > 'Add' under modes"+
            "\n\nMode selection will be checked from the top of the list to the bottom and stop at the first match so days must not overlap, or the first match will be picked."+
            "\n\nAlthough the 'Time Mode' shares names with the SmartThings modes, they do not have to have the same state.", title: "About TimeZones", required: true, state: "complete")
		}
	}
}

// TIME MODE DAY PAGE
def showTimeZoneDay(params){
	// store i otherwise lost during submitOnChange
	if(params.i != null) {state.TZID = params.i}
    def i = state.TZID
    
    // Day section to repeat
	dynamicPage(name: "showTimeZoneDay", install: false, uninstall: false) {
		section() {
        	input "dayOfWeek$i", "enum", title: "On these days:", multiple: false, required: false,
			options: ['All Week','Monday','Tuesday','Wednesday','Thursday','Friday','Saturday','Sunday','Mon to Fri','Sat to Sun','Sun to Thurs','Fri to Sat']
      	}
        
        // Time section to repeat
        def y = 2           
        for (int z = 1; z < y; z++) {        
	        def bandTitle = "Starting the day (This should be 00:00)"
            if(z!=1){bandTitle = "Then change at this time"}
            section("$bandTitle") {
                input "timeSType$i$z", "enum", title: "Starting at:", multiple: false, submitOnChange: true, required: false,
				options: ['Specified Time','Sunrise','Sunset']
                switch (settings."timeSType$i$z") {
					case "Specified Time":
						input "timeS$i$z", "time", title: "Time:", required: true
					break
					case "Sunrise":
                    case "Sunset":
						input "ofsetS$i$z", "number", range: "*..*", title: "Offset in minutes (+/-)", defaultValue: "0", required: true
					break
				}
                input "timeMode$i$z", "mode", title: "Return this mode", required: false
            }
            if(settings."timeSType$i$z" != null){y++}
		}
        section("Until the end of the day")
        
        def p = getSunriseAndSunset()
        def sRise = new Date(p.sunrise.time).format("HH:mm", location.timeZone)
        def sSet = new Date(p.sunset.time).format("HH:mm", location.timeZone)
        
        section(){
        	paragraph("1. Time bands should start at 0:00 and then must get later in each instance. \n\n2. For reference current Sunrise is ${sRise} and Sunset is ${sSet}", title: "Note:", required: true, state: "complete")
        }
   	}        
}

// GET TIMEMODE
def getTimeMode() {

	// Return ST mode if setting set
    if(settings.tUseSTMode){ return "${location.mode}" }
    
    //Find how many days there are (d)
	def d = 1
	while (true) { if(settings."dayOfWeek$d" != null) {d++} else {d=d-1; break} }
    
    //If no timezones found exit and return "None"
    if(d==0){return "None"}

	//Get Day of week (dow)
	Calendar cal = Calendar.getInstance()
    def dow = cal.get(Calendar.DAY_OF_WEEK)
    
    // Get list of OK days
    def okDays = []
    switch (dow) {
        	case "1": okDays=['All Week','Sat to Sun','Sunday','Sun to Thurs'] ;break
            case "2": okDays=['All Week','Mon to Fri','Monday','Sun to Thurs'] ;break
            case "3": okDays=['All Week','Mon to Fri','Tuesday','Sun to Thurs'] ;break
            case "4": okDays=['All Week','Mon to Fri','Wednesday','Sun to Thurs'] ;break
            case "5": okDays=['All Week','Mon to Fri','Thursday','Sun to Thurs'] ;break
            case "6": okDays=['All Week','Mon to Fri','Friday','Fri to Sat'] ;break
            case "7": okDays=['All Week','Sat to Sun','Saturday','Fri to Sat'] ;break
	}
    
    def tz = 0
    
    // Find day that matches
    for (int i = 1; i <= d; i++) { if(settings."dayOfWeek$i" in okDays ){tz=i; break} }

    //Find how many time bands there are (z) for day band
	def z = 1
	while (true) { if(settings."timeSType$tz$z" != null) {z++} else {z=z-1; break} }
    //If no timezones found exit and return "None"
    if(z==0){return "None"}
    
    //go backwards through time bands to find mode and return
    
    for (int t = z; t > 0; t=t-1) {
    
        switch (settings."timeSType$tz$t") {
			case "Specified Time":
				// IF CURRENT TIME IS GREATER THAN settings.timeS$i$t THEN RETURN settings.timeMode$i$t
                if(now() >= timeToday(settings."timeS$tz$t",location.timeZone).time){return settings."timeMode$tz$t"}
			break
			case "Sunrise":
            	// IF CURRENT TIME IS GREATER THAN SUNRISE + adjustment ofsetS$tz$z THEN RETURN settings.timeMode$tz$t
                def s = getSunriseAndSunset(sunriseOffset: settings."ofsetS$tz$t")                
                if(now() >= s.sunrise.time){return settings."timeMode$tz$t"}
            break
            case "Sunset":
				// IF CURRENT TIME IS GREATER THAN SUNSET + adjustment ofsetS$tz$z THEN RETURN settings.timeMode$tz$t
                def s = getSunriseAndSunset(sunsetOffset: settings."ofsetS$tz$t")
                if(now() >= s.sunset.time){return settings."timeMode$tz$t"}
			break
		}        
	}
    return "None"
}

def setSTMode(){
	if(settings?.tSetSTMode){
    	def modeToSetTo = getTimeMode()
    	if (location.modes?.find {it.name == modeToSetTo}) { location.setMode(modeToSetTo) }
    }
}


/*****************************************************************/
/** Multimedia
/*****************************************************************/

def showMuiltiMedia(){
    section("Players Details") {
        input(name: "mPlayerDT", type: "capability.musicPlayer", title: "Media Player Device", multiple: false, required:false)
    }
    section("Trigger These Scenes") {
    	input "mPlayScene", "enum", title: "Scene For Play", required: false, multiple: true, options: parent.moduleList(app.id, 'Scene')
       	input "mPauseScene", "enum", title: "Scene For Pause", required: false, multiple: true, options: parent.moduleList(app.id, 'Scene')
       	input "mStopScene", "enum", title: "Scene For Stop", required: false, multiple: true, options: parent.moduleList(app.id, 'Scene')
    } 
	section("Settings") {
        input "mStopDelay", "number", title: "Delay stop action", required:true, defaultValue:5
        input "mPauseDelay", "number", title: "Delay pause action", required:true, defaultValue:5
	}
    section("Special - Only currently works with Plex device types") {
    	input "mMediaTypeOk", "enum", title: "Only for media types:", multiple: true, required: false,
		options: ['movie', 'episode', 'clip', 'track']
		input "mTreatTrailersAsPause", "bool", title: "Trailers act as 'Pause'", required:false
    }
}


// Recieve command from MusicPlayer device type
def mPlayerDTCommand(evt){

	def mStatus = "$evt.value"
	def mediaType = mPlayerDT.currentplaybackType
    logWriter("Recieved event: $mStatus, with playback type: $mediaType")

	// Check if Media Type is correct
	if(settings?.mMediaTypeOk){
		def mediaTypeFound = mMediaTypeOk.find { item -> item == mediaType}
    	if(mediaTypeFound == null) {logWriter ("->Match NOT found for media type: ${mediaType}"); return}
	}   
    
	// Translate play to pause if bTreatTrailersAsPause is enabled for this room
	if(mTreatTrailersAsPause && mediaType == "clip" && mStatus == "playing") {mStatus = "paused"}

	// Unschedule delays
	unschedule(mStopCommand)
    unschedule(mPauseCommand)

// Play, Pause or Stop
	switch (mStatus) {
    	case "playing":	unschedule(); mPlayCommand(); break;
        case "paused": 	if(mPauseDelay == "0"){ mPauseCommand() }else{ runIn(mPauseDelay, mPauseCommand) }; break;
        case "stopped":	if(mStopDelay == "0"){ mStopCommand() }else{ runIn(mStopDelay, mStopCommand) }; break;
	}
}

// PLAY PAUSE STOP
def mPlayCommand(){
	if (settings?.mPlayScene){	mPlayScene?.each { activity -> parent.triggerChild(activity,null) } }	
}
def mPauseCommand(){
	if (settings?.mPauseScene){	mPauseScene?.each { activity -> parent.triggerChild(activity,null) } }	
}
def mStopCommand(){
	if (settings?.mStopScene){	mStopScene?.each { activity -> parent.triggerChild(activity,null) } }	
}



/*****************************************************************/
/** Notification
/*****************************************************************/

def showNotification(){
	section() {
        href(name: "pageNWhen", title:"", description: "Notification Rules", page: "pageNWhen", state: "complete", image: getIcon("rules"))
		href(name: "pageNMessaging", title:"", description: "Send This Message", page: "pageNMessaging", state: (nMesssageText||nDyn1? "complete" : null), image: getIcon("message"))
        href(name: "pageNDev", title:"", description: "To These Devices", page: "pageNDev", state: (nAppMsg||nSMSMsg||nAudio||nWords ? "complete" : null), image: getIcon("Noti"))
        href(name: "pageNLights", title:"", description: "Flash These Lights", page: "pageNLights", state: (nLightType ? "complete" : null), image: getIcon("Scen"))
        href(name: "pageNAlarm", title:"", description: "Trigger These Alarms", page: "pageNAlarm", state: (nAlarms ? "complete" : null), image: getIcon("sec"))
        input "nQueueType", "enum", title: "If Notification Is Already In Progress", required: true, defaultValue:"Queue", options: ["Queue","Drop","Force","Bypass"]
        paragraph(" Queue - Play after last notification finished\n Drop - If notification already in progress\n Force - Action immediately even if another notification is in progress\n Bypass - Play but don't stop subsequent notfications")
	}
}

def pageNWhen(){
	dynamicPage(name: "pageNWhen", install: false, uninstall: false) {
        section("Delay Triggered Event Until") {
            input "nDelaySecs", "number", title: "Delay For (Seconds)", required: false
            input "nMotion", "capability.motionSensor", title: "When Movement Detected On", multiple: true, required: false
            input "nContact", "capability.contactSensor", title: "Or These Contacts Close", multiple: true, required: false
            input "nCancelSecs", "number", title: "Cancel If Not Triggered After (Seconds)", required: false
    	}
	}
}

def pageNDev(){
	dynamicPage(name: "pageNDev", install: false, uninstall: false) {
	    section("In SmartThings App"){
			input "nAppMsg", "bool", title: "Send App Notification", defaultValue:false, submitOnChange: true
            if(nAppMsg){
            	input "nAppType", "enum", title: "Send This Message", required: true, submitOnChange: true,
            	options: ["Custom Message","Dynamic Message"]
            }
        }
        
        section("Mobile SMS"){
            input "nSMSMsg", "bool", title: "Send SMS", defaultValue:false, submitOnChange: true
            if(nSMSMsg){
            	input "nSMSType", "enum", title: "Send This Message", required: true, submitOnChange: true,
            	options: ["Custom Message","Dynamic Message"]
            	input "nPhone", "phone", title: "Phone Number", required: true
                paragraph("Non US users will need to use an international prefex e.g. +447971123456")
        	}
       }
       section("Audio device"){
            input "nAudio", "bool", title: "Send Audio Noification", defaultValue:false, submitOnChange: true
            if(nAudio){
            	input "nAudioPlayer", "capability.musicPlayer", title: "On This Speaker", required: true
	            input "nAudioType", "enum", title: "Play This Message Or Sound", required: true, submitOnChange: true,
    	        options: ["Custom Message","Dynamic Message","Custom Sound","Bell 1","Bell 2","Dogs Barking","Fire Alarm","The mail has arrived","A door opened","There is motion","Smartthings detected a flood","Smartthings detected smoke","Someone is arriving","Piano","Lightsaber"]
				input "nVolume", "number", title: "Temporarily change volume", description: "0-100%", required: true, defaultValue: "80"
           	}
            if(nAudioType == "Custom Sound"){
            	input "nMP3loc", "text", title:"Audio File Location (URL)", required:true
            	input "nMP3dur", "number", title: "Duration (seconds)", required: true
            }
		}
        section("Speech Synthesis device"){
            input "nWords", "bool", title: "Speak Noification", defaultValue:false, submitOnChange: true
            if(nWords){
            	input "nWordsPlayer", "capability.speechSynthesis", title: "On This Speaker", required: true
	            input "nWordsType", "enum", title: "Play This Message Or Sound", required: true, submitOnChange: true, options: ["Custom Message","Dynamic Message"]
           	}
		}
	}
}


def pageNMessaging(){
	dynamicPage(name: "pageNMessaging", install: false, uninstall: false) {
	    section("Custom Message") {
			input "nMessageText","text",title:"Custom Message", required:false, multiple: false
		}
        section("Dynamic Message (Select elements to build your dynamic message)") {            
            input "nDyn1", "enum", title: "", description: "select element", required: false, options: generateMsgText("list")
            input "nDyn2", "enum", title: "", description: "select element", required: false, options: generateMsgText("list")
            input "nDyn3", "enum", title: "", description: "select element", required: false, options: generateMsgText("list")
            input "nDyn4", "enum", title: "", description: "select element", required: false, options: generateMsgText("list")
            input "nDyn5", "enum", title: "", description: "select element", required: false, options: generateMsgText("list")
            input "nDyn6", "enum", title: "", description: "select element", required: false, options: generateMsgText("list")
            input "nDyn7", "enum", title: "", description: "select element", required: false, options: generateMsgText("list")
            input "nDyn8", "enum", title: "", description: "select element", required: false, options: generateMsgText("list")
            input "nDyn9", "enum", title: "", description: "select element", required: false, options: generateMsgText("list")
            input "nDyn10", "enum", title: "", description: "select element", required: false, options: generateMsgText("list")
		}
	}
}

def pageNLights(){
	dynamicPage(name: "pageNLights", install: false, uninstall: false) {
	    section() {
			paragraph("This is the notification lights section")
            input "nLightType", "enum", title: "Type Of Notification", required: false, submitOnChange: true,
            options: ["Flash","Flash Colour", "Custom Flash Colour"]
            if(nLightType == "Flash"){
            	input "nSwitches", "capability.switch", title: "These Lights/Switches", multiple: true
        	}
            if(nLightType == "Flash Colour"){
	            input "nRGBBulbs", "capability.colorControl", title: "These Bulbs", multiple:true, required:true
                input "nRGBColor", "enum", title: "This Colour", required: false, multiple: false, submitOnChange: true,
                options: colorOptions()
                input "nLevel", "number", range: "1..100", title: "At This Level", required: false
			}
            if (nLightType == "Flash" || nLightType == "Flash Colour"){
	            input "nFlash", "number", range: "0..10", title: "This Many Times", defaultValue: "3", submitOnChange: true
    	        input "nGap", "number", range: "0..${(10000/(nFlash ?: 1)).toInteger()}", title: "This Frequently (ms) MAX:${(10000/(nFlash ?: 1)).toInteger()}", defaultValue: "600"
			}
            if (nLightType == "Custom Flash Colour"){
            	input "nRGBBulbs", "capability.colorControl", title: "These Bulbs", multiple:true, required:true
	            href(name: "pageColourFlash", title:"", description: "Set Custom Flash Colours", page: "pageColourFlash", state: (clfColor1 ? "complete" : null), image: getIcon("Scen"))
			}
    	}
	}
}

def pageColourFlash(params){
	dynamicPage(name: "pageColourFlash", install: false, uninstall: false) {
		def totalDuration = 0
        section(){
        	input "clfRepeat", "number", range: "0..10", title: "Repeat This Many Times", defaultValue: "1", required: true
            input "clfLoop", "bool", title: "Loop through each bulb\n(Normally bulbs are grouped)", defaultValue:false
        }
        
        // section to repeat
        def x = 2           
        for (int z = 1; z < x; z++) {
            section("Colour #$z") {
                input "clfColor$z", "enum", title: "", description: "Set This Colour", required: false, multiple: false, submitOnChange: true, options: colorOptions()
                input "clfDuration$z", "number", range: "0..5000*", title: "For This Many Milliseconds (ms)", defaultValue: "800", required: true
                input "clfLevel$z", "number", range: "0..100*", title: "To This Level (optional)", required: false
            } 
            if(settings."clfColor$z" != null){x++}
        }
        //section("Duration is ${totalDuration*clfRepeat} ms (Max = 18000)")
   	}        
}

def pageNAlarm(){	
    dynamicPage(name: "pageNAlarm", install: false, uninstall: false){ 
        section("*** THIS IS NOT CODED ***")
        section() {
        	input "nAlarms", "capability.alarm",title: "Trigger These Alarms", multiple: true, required: false, submitOnChange: true
			if(nAlarms){
            	input "nAlarmType", "enum", title: "Play This Message Or Sound", required: true, defaultValue: "both",
                options: ["siren","strobe","both", "off"]
        		input "nAlarmSeconds", "number", title: "For this many seconds", required: true, defaultValue: "120"
           	}
    	}        
	}
}


def executeNotification(value){

	// Set state to say request is active, possibly pending a secondary action
	state.NotifyRequested = true
    
    // Store value if notification delayed
    state.storedValue = value as String
    
    // Store Last Notification On Parent
    parent.setLastNotification(app.id, value)
    
    // If expiration time set then set timer
    if(settings?.nCancelSecs){ runIn(nCancelSecs,nExpiredNotification) }
    
	// Scedule notification if delay set.
    if(settings?.nDelaySecs){ runIn(nDelaySecs,nNotificationEvent); return }
    
    // If no delays or secondary triggers set then notify
    if(!nDelaySecs && !nMotion && !nContact){ nNotificationEvent() }
}

def nExpiredNotification(){
	state.NotifyRequested = false
}

def notificationInProgress(value){
    if("$value" == "no"){state.notificationStatus = "no"; logWriter("notificationInProgress set to no")}
	if("$value" == "yes"){state.notificationStatus = "yes"; logWriter("notificationInProgress set to yes")}
    if("$value" == "get"){return state.notificationStatus}
}

def stopNIP(){
	parent.notificationInProgress("no")
}


def nNotificationEvent(value){
	//This is waiting for a notification from a switch or movement sensor if set (second action to trigger message)
    log.warn "JAKE"
    // Cancel any schedulded cancelations 
    unschedule(nExpiredNotification)
    
    // If a notification is currently in progress
    if(parent.notificationInProgress("get")=="yes"){
        if(nQueueType == "Queue"){
        	runIn(10,nNotificationEvent)
            if(!state.notificationTrys){state.notificationTrys = 1}else{state.notificationTrys = state.notificationTrys + 1}
            if(state.notificationTrys >= 6){stopNIP()}
            logWriter("Notification QUEUED as another notification is in progress (Attempt #${state.notificationTrys})")
            return
        }
        else if(nQueueType == "Drop"){ logWriter("Notification DROPPED as another notification is in progress."); return }
        else if(nQueueType == "Force" || nQueueType == "Bypass"){ logWriter("Notification FORCED while another notification is in progress.") }
    }
    
    if(nQueueType != "Bypass"){parent.notificationInProgress("yes")}
    
    // set state to 0
    state.notificationTrys = 0

    // Do nothing if notification has been cancelled, otherwise state as ran and run
    if(state.NotifyRequested == false){return}
    state.NotifyRequested = false


//// GET DYNAMIC MESSAGE IF SET
    def dynamicMsg = "No Message"
    try { if(nDyn1){ dynamicMsg = generateMsgText(state.storedValue) }
	}catch (Throwable t) {log.error t}


//// APP NOTIFICATION
	if(nAppMsg){ if(nAppType == "Custom Message"){ sendPush(nMessageText) }else{ sendPush("$dynamicMsg") } }

//// SMS NOTIFICATION
	if(nSMSMsg){ if(nSMSType == "Custom Message"){ sendSms(nPhone, nMessageText) }else{	sendSms(nPhone, dynamicMsg) } }
    
//// ALARM NOTIFICATION   
	if(settings?.nAlarms){ nAlarms?."${nAlarmType}()"; runIn(nAlarmSeconds,silenceAlarm)}    

//// AUDIO NOTIFICATION
	def soundToPlay = "No Message"    
	if(nAudio){
        switch (settings.nAudioType) {
			case "Bell 1":							soundToPlay = [uri: "http://s3.amazonaws.com/smartapp-media/sonos/bell1.mp3", duration: "10"];						break;
			case "Bell 2":							soundToPlay = [uri: "http://s3.amazonaws.com/smartapp-media/sonos/bell2.mp3", duration: "10"];						break;
			case "Dogs Barking":					soundToPlay = [uri: "http://s3.amazonaws.com/smartapp-media/sonos/dogs.mp3", duration: "10"];						break;
			case "Fire Alarm":						soundToPlay = [uri: "http://s3.amazonaws.com/smartapp-media/sonos/alarm.mp3", duration: "17"];						break;
			case "The mail has arrived":			soundToPlay = [uri: "http://s3.amazonaws.com/smartapp-media/sonos/the+mail+has+arrived.mp3", duration: "1"];		break;
			case "A door opened":					soundToPlay = [uri: "http://s3.amazonaws.com/smartapp-media/sonos/a+door+opened.mp3", duration: "1"];				break;
			case "There is motion":					soundToPlay = [uri: "http://s3.amazonaws.com/smartapp-media/sonos/there+is+motion.mp3", duration: "1"];				break;
			case "Smartthings detected a flood":	soundToPlay = [uri: "http://s3.amazonaws.com/smartapp-media/sonos/smartthings+detected+a+flood.mp3", duration: "2"];break;
			case "Smartthings detected smoke":		soundToPlay = [uri: "http://s3.amazonaws.com/smartapp-media/sonos/smartthings+detected+smoke.mp3", duration: "1"];	break;
			case "Someone is arriving":				soundToPlay = [uri: "http://s3.amazonaws.com/smartapp-media/sonos/someone+is+arriving.mp3", duration: "1"];			break;
			case "Piano":							soundToPlay = [uri: "http://s3.amazonaws.com/smartapp-media/sonos/piano2.mp3", duration: "10"];						break;
			case "Lightsaber":						soundToPlay = [uri: "http://s3.amazonaws.com/smartapp-media/sonos/lightsaber.mp3", duration: "10"];					break;
			case "Custom Message":					if (nMessageText) {soundToPlay = textToSpeech(nMessageText)};														break;  
            case "Dynamic Message":					soundToPlay = textToSpeech(dynamicMsg, true);																		break;
            case "Custom Sound":					soundToPlay = [uri: nMP3loc, duration: nMP3dur];
		}
        def duration = soundToPlay.duration + 3 as int
        nAudioPlayer.playTrackAndRestore(soundToPlay.uri, duration, settings?.nVolume)
	}

//// SPEAK NOTIFICATION
    def wordsToSay = "No Message"    
	if(nWords){
        switch (settings.nWordsType) {         
			case "Custom Message":					if (nMessageText) {wordsToSay = nMessageText};		break;  
            case "Dynamic Message":					wordsToSay = dynamicMsg;							break;      
		}
        nWordsPlayer.speak(wordsToSay)
	}
    
    
//// SET AS NOT RUNNING AFTER 20 SECONDS - unless audio longer
	// Cancel in progress
    def pleaseWait = 20 as int
    if(nAudio){
    	if(soundToPlay.duration != null){
			def soundDur = soundToPlay.duration as int
        	if(soundDur >= 21){ pleaseWait = "${soundToPlay.duration}" as int }
    	}
    }
	logWriter("Another Notification will be blocked for at least $pleaseWait seconds")
    runIn(pleaseWait, stopNIP)

//// LIGHTS
    if(nLightType){
    	def delay = settings.nGap
		def flashes = settings.nFlash
        def saveVals = []
       
    	switch (settings.nLightType) {
			case "Flash":
            // Get current value
            nSwitches?.each { item -> saveVals << [deviceID:item.id, switch: item.switchState.value] }
            // Now flash 
            def d = 0
       		for (int a = 1; a <= flashes; a++) { nSwitches?.on(); pause(delay); nSwitches?.off(); pause(delay) }
           	//return to previous value
            nSwitches?.each { swch ->
               	for (item in saveVals) { if (item.deviceID == swch.id) { if(item.switch == "on"){ swch.on() }else{ swch.off() } } }
    		}
			break
            
            case "Flash Colour":
            	// Save current value
                nRGBBulbs?.each { saveVals << saveHueSettings(it) }
                // Now flash
       	        def hueSetVals = []
                def d = 0
   				for (int a = 1; a <= flashes; a++) {
               		nRGBBulbs?.each { bulb ->
                    	hueSetVals = getColorByName(settings?."nRGBColor",settings?."nLevel")
                        bulb.setColor(hueSetVals)
                    }
               		pause(delay)
                    nRGBBulbs?.setLevel(5)
                    pause(delay)
                }    
                
                //return to previous value
                nRGBBulbs?.each { returnHueSettings(it, saveVals) }
    		break
            
            case "Custom Flash Colour":
            	// Save current value
                nRGBBulbs?.each { saveVals << saveHueSettings(it) }
                // Now flash
				runColourFlash(getColourFlash())
                //return to previous value
                nRGBBulbs?.each { returnHueSettings(it, saveVals) }
    		break
		}
    }
}

def runColourFlash(vars){
    def hueSetVals = []
    def runDuration = 0
    for (int x = 1; x <= clfRepeat; x++){
        vars.each { colour, level, duration ->
            if(clfLoop){
                nRGBBulbs?.each { bulb ->
                    hueSetVals = getColorByName(colour,level)
                    bulb.setColor(hueSetVals)
                }
            }else{
            	hueSetVals = getColorByName(colour,level)
            	nRGBBulbs?.setColor(hueSetVals)
            }
            // Exit if exceeding 18 seconds
            runDuration += duration
            if(runDuration >= 18000) return
            
            pause(duration)
        }
	}
}

def getColourFlash(){
    def z = 1
    def colourList = []
	while (true) {
    	if(settings."clfColor$z" != null) { colourList << [settings."clfColor$z",settings?."clfLevel$z",settings."clfDuration$z"]; z++ } else {break}
    }
    return colourList
}

def silenceAlarm(){
	if(settings?.nAlarms){ nAlarms?.off() }
}

def saveHueSettings(dev){
	def bulbTypeSet = ""
    dev.events().each { if(!bulbTypeSet && (it.name == "color" || it.name == "colorTemperature") ){bulbTypeSet = it.name} }
    return [
        deviceID:dev.id,
        hue: dev.hueState?.value ?: null,
        saturation: dev.saturationState?.value ?: null,
        level: dev.levelState?.value ?: null,
        switch: dev.switchState?.value ?: null,
        colorTemperature: dev.colorTemperatureState?.value ?: null,
        bulbTypeSetting: bulbTypeSet ?: 'color'
    ]
}

def returnHueSettings(dev, saveVals){
	//check stored value for entry
    saveVals.each {
    	if (it.deviceID == dev.id) {
            if(it.switch == "off"){
            	dev.off()
            }else if(it.bulbTypeSetting == "colorTemperature"){
                dev.setColorTemperature(it.colorTemperature.toInteger())
                dev.setLevel(it.level.toInteger())
    		}else{
            	dev.setColor([hue: it.hue.toInteger(), saturation: it.saturation.toInteger()])
                dev.setLevel(it.level.toInteger())
            }
        }
	}
}
/*

						deviceID:		bulb.id,
                    	hue:			bulb.hueState?.value ?: null,
                        saturation: 	bulb.saturationState?.value ?: null,
                        level: 			bulb.levelState?.value ?: null,
                        switch: 		bulb.switchState?.value ?: null,
                        colorTemperature: bulb.colorTemperatureState?.value ?: null,
                        bulbTypeSetting: bulbTypeSet ?: 'color'



def returnHueSettings(devID, saveVals){
	//check stored value for entry
    def hueSetVals = []
    for (hues in saveVals) {
		if (hues.deviceID == devID) {
            hueSetVals = [hue: hues.hue.toInteger(), saturation: hues.saturation.toInteger(), level: hues.level.toInteger(), switch: hues.switch]
        	return hueSetVals
    	}
	}
    //otherwise return default - warm white
    hueSetVals = [hue: 72, saturation: 20, level: 72]
    return hueSetVals
}
*/
// Generate Dynamic Message

def generateMsgText(notificationVal){

	// If request for list rerturn list.
    if(notificationVal == "list"){
    	return ["Hello [name]","Alarm status", "Number of times alarm triggered", "Doors and windows open", "People home", "The weather", "CUSTOM MESSAGE", "SYSTEM MESSAGE"]
    }
    
    // Code here to generate dynamic messages
    def msg = ""
    def msgAdd = ""
    def someWords = ""	
    
    for (int i = 1; i <= 10; i++) {
        if(settings."nDyn$i" != null){
            switch(settings."nDyn$i") {
        	case "Hello [name]":
                someWords = " " + parent.secLastPerson()
                if(someWords == " null"){someWords = ""}
                msgAdd = "Hello${someWords}, "
                break;
            case "Alarm status":
            	msgAdd = "Alarm is ${parent.state.securityState}. "
                break;
            case "Number of times alarm triggered":
            	msgAdd = "Alarm has been triggered [x] times since [date]."				//TODO
                break;
            case "Doors and windows open":
            	if(parent.state.openDoors != "") msgAdd = "${parent.state.openDoors}. " //CURRENTLY ONLY REPORTS DOORS IN SECURITY
                break;
            case "People home":
            	someWords = parent.getWhosHome("${parent.secLastPerson()}")
                if("$someWords" != "") msgAdd = "${someWords}. "
                break;
            case "The weather":
            	someWords = parent.getWeather()
                if("$someWords" != "") msgAdd = "${someWords} "
                break;
            case "CUSTOM MESSAGE":
            	if(settings.nMessageText != "") msgAdd = settings.nMessageText
                break;
            case "SYSTEM MESSAGE":
            	someWords = "$notificationVal"
                if("$notificationVal" != "null") msgAdd = "${someWords} "
                break;
			}
            // Update string if not null
        	if(msgAdd!=""){msg = msg + msgAdd + " "}
        }
    }
    return "$msg"
}


/*****************************************************************/
/** People
/*****************************************************************/

def showPresence(){
	section("Presence") {
        input "pPresence", "capability.presenceSensor", title: "Presence Sensor", required: false, multiple: false
        input "pSwitch", "capability.switch", title: "Presence Switch", required: false, multiple: false
        input "pPin1", "password", title: "Security PIN 1", required: false
        input "pPin1Sec", "enum", title: "Arm This Security Module", required: false, multiple: false, options: parent.moduleList(app.id, 'Security')
        input "pPin2", "password", title: "Security PIN 2", required: false
        input "pPin2Sec", "enum", title: "Arm This Security Module", required: false, multiple: false, options: parent.moduleList(app.id, 'Security')
        input "pPin3", "password", title: "Security PIN 3", required: false
        input "pPin3Sec", "enum", title: "Arm This Security Module", required: false, multiple: false, options: parent.moduleList(app.id, 'Security')
	}
    section("Notifications") {
        input "pNotifyArrive", "enum", title: "Notification / Scene On Arrival", required: false, multiple: true, options: parent.moduleList(app.id, 'Scene, Notification')
        input "pNotifyLeave", "enum", title: "Notification / Scene On Leaving", required: false, multiple: true, options: parent.moduleList(app.id, 'Scene, Notification')
        paragraph ("NOTE: The persons name will be passed as the 'SYSTEM VARIABLE' in notifications")    
	}
    section("Notifications") {
        input "pReport", "bool", title: "Report Presence", defaultValue: "true", required: "false"
	}
}

def pPresenceEvent(evt){

	// Don't run if disabled
	if(disabledCheck()){return}

	if(evt.value == "present"||evt.value == "on") {
        // If already present don't do anything
        if(atomicState.pPresent == "true"){ return }
        logWriter("$appName has arrived")        
        // If allowed to report presence
        if(settings?.pReport){
        	atomicState.pPresent = "true"
        	parent.securityPresence(appName, evt.value)
        }else{
        	atomicState.pPresent = "false"
        }
        if (pNotifyArrive){ pNotifyArrive?.each { activity -> parent.triggerChild(activity,settings.appName) } }  
	}
    else if (evt.value == "not present"||evt.value == "off") {
    	// Actions on leave
        atomicState.pPresent = "false"
        logWriter("$appName has left")
        if(settings?.pReport){
        	// Run scecurity check
        	parent.securityPresence(appName, evt.value)
        }
        // Notification to trigger
        if (pNotifyLeave){ pNotifyLeave?.each { activity -> parent.triggerChild(activity,settings.appName) } }        
	}
}

def isPresent(){
	// Return presence
	return atomicState.pPresent
}


/*****************************************************************/
/** Scenes  
/*****************************************************************/

def showScene(){
	
    section() {        
       	def x = 2            
       	for (int i = 1; i < x; i++) {
       		if(settings."sSceneMode$i" != null){
       			href(name: "pageModeScene$i", title:"", description: settings."sSceneMode$i", params: [i: "$i"], page: "pageModeScene", state: "complete", image: getIcon("Scen"))
       			x++
          	}else{
           		href(name: "pageModeScene", title:"", description: "Add New Scene For Mode", params: [i: "$i"], page: "pageModeScene", image: getIcon("Scen"))
       		}
		}
	}
	
    section ("Activate scene only if light level if below a certain value (optional)") {
    	input "sLightMeter", "capability.illuminanceMeasurement", title: "Light Meters", submitOnChange: true, required: false, multiple: false
		if(sLightMeter){
        	input "sLuxValue", "number", title: "Light is less than (Lux) [Currently: $sLightMeter.illuminanceState.value]", required: true, defaultValue: "50"
    	}
    }
    section("If External Trigger Set To Off") {
        input "sTriggerSceneOff", "enum", title: "Set These Scenes (optional)", required: false, multiple: true, submitOnChange: true, options: parent.moduleList(app.id, 'Scene, Notification')
	}
}

def pageModeScene(params){

	dynamicPage(name: "pageModeScene", install: false, uninstall: false) {
		// store i otherwise lost during submitOnChange
		if(params.i != null) {state.MZID = params.i}
    	def i = state.MZID
        
        section(){
        	input "sSceneMode$i", "enum", title: "When in this mode", required: false, multiple: true, submitOnChange: true, options: getModeList()
        }
    	section("Lights") {
			input "sDimmers$i", "capability.switchLevel", title: "Adjust level of these bulbs", multiple: true, required: false, submitOnChange: true
        	input "sRgbBulbs$i", "capability.colorControl", title: "Adjust level and color of these bulbs", multiple:true, required:false, submitOnChange: true
        
        	if(settings."sRgbBulbs$i"||settings."sDimmers$i") {
        		input "sBLevel$i", "number", range: "0..100", title: "To This Level (Optional for Colors)", required: false
        	
        		if(settings."sRgbBulbs$i") {
        			input "sBColor$i", "enum", title: "And This Colour", required: false, multiple: false, submitOnChange: true, options: colorOptions()
                    input "sBColorTemp$i", "enum", title: "Or This Color Temperature (°K)", required: false, multiple: false, options: colorTempOpts()
            	}
            input "sDimOnlyIfOn$i", "bool", title: "Adjust bulbs only if they are already on", defaultValue:false
            }
		}
		section("Switches") {
       		input "sOnSwitches$i", "capability.switch", title:"Turn On", multiple: true, required: false
        	input "sOffSwitches$i", "capability.switch", title:"Turn Off", multiple: true, required: false            
    	}
        section("Trigger These Additional Actions"){
        	input "sTriggerAdditional", "enum", title: "Set These Scenes / Notifications", required: false, multiple: true, submitOnChange: true, options: parent.moduleList(app.id, 'Scene, Notification')
            input "appList", "enum", title: "Trigger These appLink Events", required: false, submitOnChange: true, multiple: true, options: parent.appLinkHandler(value: "list")
        }
	}
}


def executeScene(evt){
    if(!evt){evt="on"}
    
    // If off event recieved then trigger an off scene instead
    if("off" == evt.value){	if (sTriggerSceneOff){ sTriggerSceneOff?.each { activity -> parent.triggerChild(activity,null) } }; return }

    // Check luminance and exit if not below specified lux value
    if(sLightMeter){
    	def curLight = sLightMeter.illuminanceState.value as int
    	def reqLight = settings.sLuxValue as int
    	if(curLight >= reqLight) { logWriter("Exited as too light"); return	}
    }
    
    // Find scene to trigger based on mode
	def d = "1"
    def i = 0
    def tm = parent.getTimeMode()
	while (settings."sSceneMode$d" != null && d !=0) {	
    	settings."sSceneMode$d".each { scene -> if(scene == tm || scene == "Any"){ i = d } }
        if(i>0){d = 0}else{d++}
	}
	//If no match exit
	if(i == 0){logWriter("Exited as mode not found in scene") ;return}
	
	// Set lights
	if ("sBLevel$i" != null) {
    	
    	def hueSetVals = getColorByName(settings?."sBColor$i",settings?."sBLevel$i")
       
        if (sDimOnlyIfOn$i){
        	settings."sDimmers$i"?.each { bulb -> if ("on" == bulb.currentSwitch) {bulb.setLevel(settings."sBLevel$i")}}
            settings."sRgbBulbs$i"?.each { hue -> if ("on" == hue.currentSwitch) {
            	if(hueSetVals){
                	hue.setColor(hueSetVals)
                }else{
                	hue.setColorTemperature(settings?."sBColorTemp$i")
                    if(settings?."sBLevel$i" != null){hue.setLevel(settings."sBLevel$i")}
                }
            }}
        }else{
        	
        	if("sBLevel$i" != "0"){
	           	settings."sDimmers$i"?.setLevel(settings."sBLevel$i")  
                if(hueSetVals){
                	settings."sRgbBulbs$i"?.setColor(hueSetVals)
                }else{
                	settings."sRgbBulbs$i"?.setColorTemperature(settings?."sBColorTemp$i")
                    if(settings?."sBLevel$i"){settings."sRgbBulbs$i"?.setLevel(settings."sBLevel$i")}
                }
        	}else{
            	//if level is 0 then just turn off
            	settings."sRgbBulbs$i"?.off()
            	settings."sDimmers$i"?.off()
            }
        }
	}
    
	// Set switches
	settings."sOnSwitches$i"?.on()
    settings."sOffSwitches$i"?.off()
    if (settings?.sTriggerAdditional){ sTriggerAdditional?.each { activity -> parent.triggerChild(activity,null) } }
    if(settings?.appList){ appList.each { app -> parent.appLinkHandler(value: "run", data: app) } }
    //if (settings?.sTriggerSceneCancel){ sTriggerSceneCancel?.each { activity -> parent.triggerChild(activity,"cancel") } } //NOT SURE WHAT THIS ACTUALLY DOES?
}

/*****************************************************************/
/** Security Module
/*****************************************************************/


def showSecZone(params){
	def actions = location.helloHome?.getPhrases()*.label
    section("On Arming") {
    	input "routineArmed", "enum", title: "Routine To Trigger When Armed", required: false, options: actions
    }
    if(parent.inDev){///////////////////////////////// IN DEVELOPMENT
    
    section("When One Of These Are Triggered") {
        input "secMotion", "capability.motionSensor", title: "Movement Sensors", description: "", multiple: true, required: false
        input "secContact", "capability.contactSensor", title: "Contact Sensors", description: "", multiple: true, required: false
    }
    section("Do This Immediately") {
        input "secSceneNotifyA", "enum", title: "Trigger These Scenes / Notifications", required: false, description: "", multiple: true, options: parent.moduleList(app.id, 'Scene, Notification')
    }
    section("Do This After A Delay (Unless Disarmed)"){
        input "secDelay", "number", title: "After This Many Seconds", defaultValue: "60", required: false
        input "secSceneNotifyB", "enum", title: "Trigger These Scenes / Notifications", required: false, description: "", multiple: true, options: parent.moduleList(app.id, 'Scene, Notification')
    }
    section("Disable Security If These Sensors Are Triggered") {
        input "secMotionDis", "capability.motionSensor", title: "Movement Sensors", multiple: true, description: "", required: false
        input "secContactDis", "capability.contactSensor", title: "Contact Sensors", multiple: true, description: "", required: false
        input "secSceneNotifyC", "enum", title: "Trigger These Scenes / Notifications", required: false, multiple: true, description: "", options: parent.moduleList(app.id, 'Scene, Notification')
    }
    }///////////////////////////////// IN DEVELOPMENT END
    section("Vacation Mode") {
    	input "secVacationEnabled", "bool", title: "Enable Vacation Mode", defaultValue: "false", required: "false"
    }
}

def executeSecurityMode(value){
	// Activate this security mode
	if(value == "on"){ parent.securityState(settings.appName) }
}

/*****************************************************************/
/** Security Main
/*****************************************************************/

def pageSecurity(){
	dynamicPage(name: "pageSecurity", install: false, uninstall: false) {
    	section("*** THIS IS USED AT YOUR OWN RISK ***")
	    section() {
            input "sEnable", "bool", title: "Enable SmarterHome Security", defaultValue: "false", required: "false"
        }
        section("Security & Safety") {
            href(name: "pageSecArming", title:"", description: "Arm / Disarm", page: "pageSecArming", state: (secAutoArm ? "complete" : null), image: getIcon("sec"))
            href(name: "pageSecDoorOpen", title:"", description: "Doors Left Open", page: "pageSecDoorOpen", state: (secDoorContact ? "complete" : null), image: getIcon("sec"))
            href(name: "pageSecVacation", title:"", description: "Vacation Mode", page: "pageSecVacation", state: (secVacationModes? "complete" : null), image: getIcon("holiday"))
        }
            
            // TODO: Main logic should for arming based on absence should exist in the parent app..
            
            // Security Module Should contain:
            
            //// - Auto disable nighttime mode when xyz sensorr is triggered (providing downstairs sensor has not already been triggered)
            //// Notify when alarm is set if XYZ doors or windows are open
            //// When Triggered needs delay to allow disarming with notification
            //// When ALARM needs to trigger new notificaiton and also siren
            //// Holiday lighting mode - part of security child app, perhaps a new child app or scene? But perhaps linked to the alarm
            
            
            // Security Main App
            
            //// Needs to hold main Alarm "State"
            //// Needs to track how many times alarm has been triggered since last check - perhaps include date/time it was last reported
            //// Set timer for clearing last person to arrive
            //// When to set and unset alarm
            //// - When everyone leaves
            //// - When manually set via keypad (with delay) - keypad should have unique codes per person.
            //// - Child app will need to specify an Security module to activate per code and also similate presence (only where no presence device)
            //// - Auto Arm? When no movement on XYZ sensors for x minutes
            
            //// Need to add restrictions to Zone based on Alarm State.
            
            //// Fire and flood alarms also.
            
            ////input "locks", "capability.lock",title: "Locks", multiple: true, required: false
	}
}

def pageSecArming(params){	
    dynamicPage(name: "pageSecArming", install: false, uninstall: false){ 
        section("Generic Arm Settings") {
        	//input "keypad", "capability.lockCodes", title: "Keypad Input", multiple: false, required: false
        	input "secArmDelay", "number", title: "Arm After This Many Seconds", defaultValue: "60", required: false
    	}
        section("Auto Arm When Everyone Leaves") {
        	input "secAutoArm", "bool", title: "Arm Security When Everyone Leaves", defaultValue: "false", required: "false"
            input "secAutoArmMode", "enum", title: "Set This Security Mode", required: false, description: "", multiple: false, options: moduleList(app.id, 'Security')
    	}
        def actions = location.helloHome?.getPhrases()*.label
        section("Disarming") {
            input "routineDisarmed", "enum", title: "Routine to trigger when Disarmed", options: actions
    	}
	}
}

def pageSecVacation(){
	dynamicPage(name: "pageSecVacation", install: false, uninstall: false) {
	    section("Vacation Mode") {
        	input "secVacationModes", "enum", title: "When In These Modes", required: false, multiple: true, options: getModeList()
            input "secSceneVacation", "enum", title: "Set These Scenes / Notifications", required: false, multiple: true, description: "", options: moduleList(app.id, 'Scene, Notification')
            input "secRandomSceneVacation", "enum", title: "Set These Scenes / Notifications Ramdomly", required: false, multiple: true, description: "", options: moduleList(app.id, 'Scene, Notification')
            input "secRandVacationPercent", "number", range: "0..100", title: "% Chance Each Scene Will Trigger,\nEvery 15 Mins", defaultValue: "33"
            input "secSceneVacationOff", "enum", title: "Otherwise Set These Off Scenes", required: false, multiple: true, description: "", options: moduleList(app.id, 'Scene, Notification')
    	}
        section("Instructions"){
        	paragraph 	"Random Scenes should include both on and off scenes, each one will trigger randomly based on the % you specify" +
            			" and this is triggered about every 15 minutes."+
                        "\nExiting the app by pressing 'Done' on the main screen will also force the random scenes." +
                        "\nAs each scene is individually triggered randomly then both the on and off scene may trigger in the same period."
        }
	}
}

def executeVacation(value){
    childApps.each { child ->
    	if(child.secVacationEnabled && child.appName == securityState("get")) {
            def timeMode = getTimeMode()
            if (settings?.secVacationModes != null && (secVacationModes.contains("Any") || secVacationModes.contains(timeMode))) {
                // On Scenes
                if (secSceneVacation){ secSceneVacation?.each { activity -> triggerChild(activity, null) } }
                // Random scenes
                secRandomSceneVacation.each{ activity ->
                    int randVal = Math.random() *100 as Integer
                    if(randVal <= secRandVacationPercent){ triggerChild(activity, null) }
                }
            }else{
            	value = "off"
        	}
        }
    }
    if(value == "off"){
    	// Off scenes
    	if (secSceneVacationOff){ secSceneVacationOff?.each { activity -> triggerChild(activity, null) } }
    }
}


def securityState(value){
	// Push to parent
	if(parent){parent.securityState(value)}
    
    // Return status
	if(value == "get"){
    	if(!state?.securityState) { state?.securityState = "OFF" }
        return state.securityState
    }
    
    // Turn Off
	if(value == "off"){
    	unschedule(setSecurityState)
    	state.securityState = "OFF"
        executeVacation("off")
        location.helloHome?.execute(settings.routineDisarmed)
        return
    }
    
    // Set state after delay
    if(!secArmDelay || secArmDelay == 0){
    	setSecurityState([status: value])
    }else{
    	runIn(secArmDelay, setSecurityState, [data: [status: value]])
    }
}

def setSecurityState(data) {
    state.securityState = data.status
    // Trigger Routine specified in child security app
    childApps.each { child -> if(child.modType == "Security" && child.appName == data.status) { location.helloHome?.execute(child.routineArmed) } }
}


/*****************************************************************/
/** Security - People
/*****************************************************************/
def securityPresence(name,event){

	//push to parent
    if(parent) parent.securityPresence(name,event)

	// If name = null then person has left so don't have to store last person name
	if(event == "present" || event == "on"){
    	state.lastPerson = name
        // This will wipe last person after x seconds - may not be useful after some time
    	if(settings?.clearLastPersonDelay != "null" && settings?.clearLastPersonDelay >= "1"){ runIn(1200,clearLastPerson) }
    }
    
    // Update Log
    switch(event) {
       	case "present":
        case "on":
        	event = "Arrived"
        break;
        case "not present":
        case "off":
        	event = "Departed"
        break;
    }
    updateLog("set", "People", settings?.evtLogNum, "${name} [${event}]")
    
    // Find out if anyone is home
    def peopleHome = false
	childApps.each { child -> if(child.isPresent() == "true") { peopleHome = true }	}
    
    // If people home turn off alarm
    if(peopleHome){	securityState("off") }
    else if(secAutoArm){ triggerChild(secAutoArmMode,"on") } // Set alarm, if auto arming is turned on.
}

// return last person and clear after 30 seconds
def secLastPerson(){
    runIn(30,clearLastPerson)
    return state.lastPerson
}

// Clear Last Person
def clearLastPerson(){
	state.lastPerson = null
}

/*****************************************************************/
/** Security - Doors Open
/*****************************************************************/
def pageSecDoorOpen(){	
    dynamicPage(name: "pageSecDoorOpen", install: false, uninstall: false){ 
		section("") {
        	input "secDoorContact", "capability.contactSensor", title: "When Any Of These Doors Are Left Open", description: "", multiple: true, required: false
            input "secDoorDelay", "number", title: "For This Many Seconds", defaultValue: "120", required: true
            input "secDoorNotification", "enum", title: "Trigger These Notifications", required: false, description: "", multiple: true, options: moduleList(app.id, 'Notification')
    	}
	}
}

def secDoorHandler(evt){
	def doorsOpen = ""
    def suffix = " is open"
    
    secDoorContact.each { contact ->
    	if(contact.contactState.value == "open"){
        	if(doorsOpen == ""){
            	doorsOpen = contact.name
            }else{
            	doorsOpen = doorsOpen +", "+ contact.name
                suffix = " are open"
            }
        }
    }
    if(doorsOpen == ""){
    	unschedule(secDoorAlert)
        atomicState.openDoors = ""
        logWriter("All Doors Now Closed")
    }else{
    	atomicState.openDoors = "The " + doorsOpen + suffix
    	runIn(secDoorDelay,secDoorAlert)
        logWriter("${atomicState.openDoors} Starting Timer")
    }
}

def secDoorAlert(params){
    if (secDoorNotification){ secDoorNotification?.each { activity -> triggerChild(activity,secDoorNotification) } }
}

/*****************************************************************/
/** Status
/*****************************************************************/

def pageStatus(){
	dynamicPage(name: "pageStatus", title: "System Status", install: false, uninstall: false) {
		
        section("Current Time Mode"){
        	paragraph(getTimeMode())
        }
        section("Current Security Mode"){
            paragraph("${state.securityState}")
    	}
        section("Who's Home"){
            paragraph(getWhosHome())
    	}
        
        section("Today's Weather"){
            paragraph(getWeather())
    	}
        section("People Activity"){
        	input "evtLogNum", "number", title: "Number Of Rows To Log", required: true, defaultValue: 20
            paragraph("${updateLog("get", "People", evtLogNum, null)}")
    	}
	}
}


/*****************************************************************/
/** Weather
/*****************************************************************/


def pageWeather() {
	dynamicPage(name: "pageWeather", title: "Weather Settings") {
		section {
    		input "wImperial", "bool", title: "Report Weather In Imperial Units\n(°F / MPH)", defaultValue: "false", required: "false"
            input "wZipCode", "text", title: "Zip Code (If Location Not Set)", required: "false"
            paragraph("Currently forecast is automatically pulled from getWeatherFeature your location must be set in your SmartThings app for this to work.")
		}
	}
}

def getWeather(){
	def result ="Today's weather is unavailable"
	try {
    	def weather = getWeatherFeature("forecast", settings.wZipCode)
    	if(settings.wImperial){
			result = "Today's forcast is " + weather.forecast.txt_forecast.forecastday[0].fcttext + " Tonight it will be " + weather.forecast.txt_forecast.forecastday[1].fcttext
	    }else{
    		result = "Today's forcast is " + weather.forecast.txt_forecast.forecastday[0].fcttext_metric + " Tonight it will be " + weather.forecast.txt_forecast.forecastday[1].fcttext_metric
	    }
		return result
	}
	catch (Throwable t) {
		log.error t
        return result
	}
}

/*****************************************************************/
/** Zones
/*****************************************************************/


def showZone(){
	section("Settings For This Zone") {
		
        href(name: "pageZOccupied", title:"", description: "When Occcupied", page: "pageZOccupied", state: (zMotion||zContact ? "complete" : null), image: getIcon("eye"))
        if(parent.inDev)href(name: "pageZTemperature", title:"", description: "Climate", page: "pageZTemperature", state: (zTemp ? "complete" : null), image: getIcon("temp"))
    }

}

// ZONE PAGE
def pageZOccupied(params){
	dynamicPage(name: "pageZOccupied", install: false, uninstall: false) {
		section("If Movement Detected") {
        	input "zMotion", "capability.motionSensor", title: "On These Sensors", multiple: true, required: false
            input "zContact", "capability.contactSensor", title: "Or These Contacts", multiple: true, required: false
        	input "zScene", "enum", title: "Trigger These Scenes / Notifications", required: false, multiple: true, submitOnChange: true, options: parent.moduleList(app.id, 'Scene, Notification')
    	}
    	section("If Movement Stops") {
        	input "zSceneOff", "enum", title: "Trigger These Scenes / Notifications", required: false, multiple: true, options: parent.moduleList(app.id, 'Scene, Notification')
        	input "zOffDelay", "number", title: "After This Many Seconds", defaultValue: "300", required: false
    	}
        section("Unless") {
        	input "zDisableSwitch", "capability.switch", title: "This Swtich Is On", required: false, multiple: false, description: null
            input "zPwrMeter", "capability.powerMeter", title: "This Power Meter", required: false, submitOnChange: true, multiple: false, description: null
            if(settings.zPwrMeter){
            	input "zPwrWatts", "number", title: "Is above (Watts) [Current: ${zPwrMeter.powerState.value}W]", defaultValue: "15", required: true, description: "Watts"
            }
        }
   	}        
}


def pageZTemperature(params){
	dynamicPage(name: "pageZOccupied", install: false, uninstall: false) {
		section("Temperature Management") {
        	input "zTemp", "capability.temperatureMeasurement", title: "Temperature Sensor", required: true, multiple: false
        	if(zTemp){
        		paragraph("Current temperature is ${zTemp.temperatureState.value}°")
       		}
            paragraph("STILL NO IDEA WHAT I AM DOING WITH THIS AS I HAVE NO THERMOSTAT OR HEATING SYSTEM TO CONTROL!")
    	}
   	}        
}


def zMotionDetected(evt) {
    // If movement or door sensor detected
	// unschedule existing timers
    unschedule(zMotionExpired)
	// if event is inactive or closed then set timer
    if(zOffDelay){ if(evt.value in ["inactive", "closed"]){ runIn(zOffDelay,zMotionExpired) } }
    
    // Request scene / notification if scene not already requested prior to expiration
    if(!state.zMotionAlive){
    	if(disabledCheck()){return}
    	state.zMotionAlive = true
       	if (zScene){ zScene?.each { activity -> parent.triggerChild(activity,settings.appName) }
        logWriter("Motion Detected and Actions Triggered")
    	}
	}    
}

def zMotionExpired(evt) {
	
    if(disabledCheck()){
        if(settings.zOffDelay){runIn(settings.zOffDelay,zMotionExpired)}
        logWriter("Off scene not set as disable switch is on, trying again in $settings.zOffDelay seconds")
        return
    }

    // If Power Level Override in place then re-delay
	if(zPwrMeter){
        def currentPwr = zPwrMeter.powerState.value as double
        def requiredPwr = settings.zPwrWatts.value as double
    	if(currentPwr >= requiredPwr){
        	if(settings.zOffDelay){runIn(settings.zOffDelay,zMotionExpired)}
            logWriter("Off scene not set as power outlet above defined value, trying again in $settings.zOffDelay seconds")
            return
        }
    }
	// Otherwise turn off lighting
    state.zMotionAlive = false
    logWriter("No motion detected for defined time")
    if (settings?.zSceneOff){ zSceneOff?.each { activity -> parent.triggerChild(activity,settings.appName) } }
}




/*****************************************************************/
/** Settings
/*****************************************************************/

// Settings Page
def pageSettings(params) {
	dynamicPage(name: "pageSettings", title: "Settings", install: false, uninstall: true) {
        section("Settings") {
        	href "pageTimeZones", title: "Modes / Time Modes", image: getIcon("time"), required: false
            href "pageWeather", title: "Weather", image: getIcon("weather"), required: false
            input "clearLastPersonDelay", "number", title: "Wipe Last Person To Arrive After (seconds)", required:true, defaultValue:900
            input "triggerRoutine", "enum", title: "Repeat Last Notification When Routine Runs", required: false, options: getRoutines()
            
            input "remoteListSelect", "enum", title: "Items To Appear In Remote Control", required: false, multiple: true, options: moduleList(app.id, 'Scene, Notification')
            input "appLinkListSelect", "enum", title: "Items To Be Published To AppLink (Exit App To Update)", required: false, multiple: true, options: moduleList(app.id, 'Scene, Notification')
        }
        section("About") {
			paragraph app.version(), title: "App Version", required: false
		}
        section(title: "Debugging") {
			input "debugLogging", "bool", title: "Enable debug logging", defaultValue: false, submitOnChange: true, required: false
            input "inDev", "bool", title: "Enable 'In Development' Functionality", defaultValue: false, submitOnChange: true, required: false
		}        
	}
}

// Debug Logging
private def logWriter(value) {
	if(parent){	if(parent.debugLogging) {log.warn "${app.label} >> ${value}"}
    }else{ if(debugLogging) {log.warn "${app.label} >> ${value}"} }
}


/*****************************************************************/
/** LISTS
/*****************************************************************/

// GET LIST OF MODULES
def moduleList(appID, modType) {
	def result = [:]
	childApps.each { child ->
		if(child.id != appID && modType.contains("${child.modType}")){
        	result << ["${child.id as String}" : "${child.label as String}"]    
        }        
	}
	return result.sort { a, b -> a.value.toLowerCase() <=> b.value.toLowerCase() }
}

// GET LIST OF MODES WITH "Any" First in list..
def getModeList(){
	def list = ["Any"]
    location.modes.each {list << "$it"}
	return list
}

// GET ALARM MODES
def getSecModeList(){
	def list = ["Any","OFF"]
    childApps.each { child ->
    	if(child.modType == "Security") list << child.appName
    }
	return list.sort()
}

// GET LIST OF ROUTINES
def getRoutines() {
	def result = [:]    
    location.helloHome?.getPhrases().each { routine -> result << ["${routine.id as String}" : "${routine.label as String}"] }
	return result.sort { a, b -> a.value.toLowerCase() <=> b.value.toLowerCase() }
}

// GET LIST OF WHO IS HOME
def getWhosHome(exclude) {
	def result = ""
    def count = 0
    childApps.each { child ->
		if(child.modType=="People" && child.appName != exclude){
        	if(child.isPresent()){
            	count ++
            	if(result != ""){
                	result = result + ", "
                }
            	result = result + child.appName
            }
        }    
	}
    if (count ==1){result = result + " is home"}
    else if (count >=2){result = result + " are home"}
	return result
}

// Get List for Remote in alphabetical order
def remoteList() {
	def result = [:]
	childApps.each { child ->
		if(remoteListSelect.contains(child.id)){result << ["${child.id as String}" : "${child.label as String}"]}
	}
	return result.sort { a, b -> a.value.toLowerCase() <=> b.value.toLowerCase() }
}

// Return Icon
def getIcon(value){
	switch (value) {
		case "home":		return "https://raw.githubusercontent.com/jebbett/SmarterHome/master/icons/SH.png" ;break
        case "remo":		return "https://raw.githubusercontent.com/jebbett/SmarterHome/master/icons/MB__remote.png" ;break
		case "cp":			return "https://raw.githubusercontent.com/jebbett/SmarterHome/master/icons/MB__Control-Panel.png" ;break
		case "apps":		return "https://raw.githubusercontent.com/jebbett/SmarterHome/master/icons/MB__Apps.png" ;break
		case "sec":			return "https://raw.githubusercontent.com/jebbett/SmarterHome/master/icons/MB__LOCK.png" ;break
		case "info":		return "https://raw.githubusercontent.com/jebbett/SmarterHome/master/icons/MB__info.png" ;break
        case "plus":		return "https://raw.githubusercontent.com/jebbett/SmarterHome/master/icons/MB__plus.png" ;break
        case "play":		return "https://raw.githubusercontent.com/jebbett/SmarterHome/master/icons/MB__WMP.png" ;break
        case "calendar":	return "https://raw.githubusercontent.com/jebbett/SmarterHome/master/icons/MB__calendar.png" ;break
        case "rules":		return "https://raw.githubusercontent.com/jebbett/SmarterHome/master/icons/MB__tasks.png" ;break
        case "message":		return "https://raw.githubusercontent.com/jebbett/SmarterHome/master/icons/MB__mail.png" ;break
        case "Noti":		return "https://raw.githubusercontent.com/jebbett/SmarterHome/master/icons/MB__msg.png" ;break
        case "Scen":		return "https://raw.githubusercontent.com/jebbett/SmarterHome/master/icons/MB__light.png" ;break
        case "fireflood":	return "https://raw.githubusercontent.com/jebbett/SmarterHome/master/icons/MB_Fire.png" ;break
        case "holiday":		return "https://raw.githubusercontent.com/jebbett/SmarterHome/master/icons/MB__world.png" ;break
        case "eye":			return "https://raw.githubusercontent.com/jebbett/SmarterHome/master/icons/MB__viewer.png" ;break
        case "temp":		return "https://raw.githubusercontent.com/jebbett/SmarterHome/master/icons/MB_Temperature.png" ;break
        case "time":		return "https://raw.githubusercontent.com/jebbett/SmarterHome/master/icons/MB__clock.png" ;break
        case "weather":		return "https://raw.githubusercontent.com/jebbett/SmarterHome/master/icons/MB__weather.png" ;break
        case null:			return "https://raw.githubusercontent.com/jebbett/SmarterHome/master/icons/MB__Control-Panel.png" ;break
	}
}

// RETURN HUE SETTINGS BASED ON COLOUR


private getColorByName(cName, level) {
	if (cName == "Random") {    	
        int hueLevel = !level ? 100 : level
        int hueHue = Math.random() *100 as Integer
        def randomColor = [hue: hueHue, saturation: 100, level: hueLevel]
		return randomColor
	}
    
    for (color in colors()) {
		if (color.name == cName) {
        	int hueVal = Math.round(color.h / 3.6)
            int hueLevel = !level ? color.l : level
			def hueSet = [hue: hueVal, saturation: color.s, level: hueLevel]
            return hueSet
		}
	}
	logWriter("Color Match Not Found")
}

private colors() {
	return [
    	[ name: "Random",					rgb: "#000000",		h: 0,		s: 0,		l: 0,	],
		[ name: "Soft White",				rgb: "#B6DA7C",		h: 83,		s: 44,		l: 67,	],
		[ name: "Warm White",				rgb: "#DAF17E",		h: 51,		s: 20,		l: 100,	],
        [ name: "Very Warm White",			rgb: "#DAF17E",		h: 51,		s: 60,		l: 51,	],
		[ name: "Daylight White",			rgb: "#CEF4FD",		h: 191,		s: 9,		l: 90,	],
		[ name: "Cool White",				rgb: "#F3F6F7",		h: 187,		s: 19,		l: 96,	],
		[ name: "White",					rgb: "#FFFFFF",		h: 0,		s: 0,		l: 100,	],
		[ name: "Alice Blue",				rgb: "#F0F8FF",		h: 208,		s: 100,		l: 97,	],
		[ name: "Antique White",			rgb: "#FAEBD7",		h: 34,		s: 78,		l: 91,	],
		[ name: "Aqua",						rgb: "#00FFFF",		h: 180,		s: 100,		l: 50,	],
		[ name: "Aquamarine",				rgb: "#7FFFD4",		h: 160,		s: 100,		l: 75,	],
		[ name: "Azure",					rgb: "#F0FFFF",		h: 180,		s: 100,		l: 97,	],
		[ name: "Beige",					rgb: "#F5F5DC",		h: 60,		s: 56,		l: 91,	],
		[ name: "Bisque",					rgb: "#FFE4C4",		h: 33,		s: 100,		l: 88,	],
		[ name: "Blanched Almond",			rgb: "#FFEBCD",		h: 36,		s: 100,		l: 90,	],
		[ name: "Blue",						rgb: "#0000FF",		h: 240,		s: 100,		l: 50,	],
		[ name: "Blue Violet",				rgb: "#8A2BE2",		h: 271,		s: 76,		l: 53,	],
		[ name: "Brown",					rgb: "#A52A2A",		h: 0,		s: 59,		l: 41,	],
		[ name: "Burly Wood",				rgb: "#DEB887",		h: 34,		s: 57,		l: 70,	],
		[ name: "Cadet Blue",				rgb: "#5F9EA0",		h: 182,		s: 25,		l: 50,	],
		[ name: "Chartreuse",				rgb: "#7FFF00",		h: 90,		s: 100,		l: 50,	],
		[ name: "Chocolate",				rgb: "#D2691E",		h: 25,		s: 75,		l: 47,	],
		[ name: "Coral",					rgb: "#FF7F50",		h: 16,		s: 100,		l: 66,	],
		[ name: "Corn Flower Blue",			rgb: "#6495ED",		h: 219,		s: 79,		l: 66,	],
		[ name: "Corn Silk",				rgb: "#FFF8DC",		h: 48,		s: 100,		l: 93,	],
		[ name: "Crimson",					rgb: "#DC143C",		h: 348,		s: 83,		l: 58,	],
		[ name: "Cyan",						rgb: "#00FFFF",		h: 180,		s: 100,		l: 50,	],
		[ name: "Dark Blue",				rgb: "#00008B",		h: 240,		s: 100,		l: 27,	],
		[ name: "Dark Cyan",				rgb: "#008B8B",		h: 180,		s: 100,		l: 27,	],
		[ name: "Dark Golden Rod",			rgb: "#B8860B",		h: 43,		s: 89,		l: 38,	],
		[ name: "Dark Gray",				rgb: "#A9A9A9",		h: 0,		s: 0,		l: 66,	],
		[ name: "Dark Green",				rgb: "#006400",		h: 120,		s: 100,		l: 20,	],
		[ name: "Dark Khaki",				rgb: "#BDB76B",		h: 56,		s: 38,		l: 58,	],
		[ name: "Dark Magenta",				rgb: "#8B008B",		h: 300,		s: 100,		l: 27,	],
		[ name: "Dark Olive Green",			rgb: "#556B2F",		h: 82,		s: 39,		l: 30,	],
		[ name: "Dark Orange",				rgb: "#FF8C00",		h: 33,		s: 100,		l: 50,	],
		[ name: "Dark Orchid",				rgb: "#9932CC",		h: 280,		s: 61,		l: 50,	],
		[ name: "Dark Red",					rgb: "#8B0000",		h: 0,		s: 100,		l: 27,	],
		[ name: "Dark Salmon",				rgb: "#E9967A",		h: 15,		s: 72,		l: 70,	],
		[ name: "Dark Sea Green",			rgb: "#8FBC8F",		h: 120,		s: 25,		l: 65,	],
		[ name: "Dark Slate Blue",			rgb: "#483D8B",		h: 248,		s: 39,		l: 39,	],
		[ name: "Dark Slate Gray",			rgb: "#2F4F4F",		h: 180,		s: 25,		l: 25,	],
		[ name: "Dark Turquoise",			rgb: "#00CED1",		h: 181,		s: 100,		l: 41,	],
		[ name: "Dark Violet",				rgb: "#9400D3",		h: 282,		s: 100,		l: 41,	],
		[ name: "Deep Pink",				rgb: "#FF1493",		h: 328,		s: 100,		l: 54,	],
		[ name: "Deep Sky Blue",			rgb: "#00BFFF",		h: 195,		s: 100,		l: 50,	],
		[ name: "Dim Gray",					rgb: "#696969",		h: 0,		s: 0,		l: 41,	],
		[ name: "Dodger Blue",				rgb: "#1E90FF",		h: 210,		s: 100,		l: 56,	],
		[ name: "Fire Brick",				rgb: "#B22222",		h: 0,		s: 68,		l: 42,	],
		[ name: "Floral White",				rgb: "#FFFAF0",		h: 40,		s: 100,		l: 97,	],
		[ name: "Forest Green",				rgb: "#228B22",		h: 120,		s: 61,		l: 34,	],
		[ name: "Fuchsia",					rgb: "#FF00FF",		h: 300,		s: 100,		l: 50,	],
		[ name: "Gainsboro",				rgb: "#DCDCDC",		h: 0,		s: 0,		l: 86,	],
		[ name: "Ghost White",				rgb: "#F8F8FF",		h: 240,		s: 100,		l: 99,	],
		[ name: "Gold",						rgb: "#FFD700",		h: 51,		s: 100,		l: 50,	],
		[ name: "Golden Rod",				rgb: "#DAA520",		h: 43,		s: 74,		l: 49,	],
		[ name: "Gray",						rgb: "#808080",		h: 0,		s: 0,		l: 50,	],
		[ name: "Green",					rgb: "#008000",		h: 120,		s: 100,		l: 25,	],
		[ name: "Green Yellow",				rgb: "#ADFF2F",		h: 84,		s: 100,		l: 59,	],
		[ name: "Honeydew",					rgb: "#F0FFF0",		h: 120,		s: 100,		l: 97,	],
		[ name: "Hot Pink",					rgb: "#FF69B4",		h: 330,		s: 100,		l: 71,	],
		[ name: "Indian Red",				rgb: "#CD5C5C",		h: 0,		s: 53,		l: 58,	],
		[ name: "Indigo",					rgb: "#4B0082",		h: 275,		s: 100,		l: 25,	],
		[ name: "Ivory",					rgb: "#FFFFF0",		h: 60,		s: 100,		l: 97,	],
		[ name: "Khaki",					rgb: "#F0E68C",		h: 54,		s: 77,		l: 75,	],
		[ name: "Lavender",					rgb: "#E6E6FA",		h: 240,		s: 67,		l: 94,	],
		[ name: "Lavender Blush",			rgb: "#FFF0F5",		h: 340,		s: 100,		l: 97,	],
		[ name: "Lawn Green",				rgb: "#7CFC00",		h: 90,		s: 100,		l: 49,	],
		[ name: "Lemon Chiffon",			rgb: "#FFFACD",		h: 54,		s: 100,		l: 90,	],
		[ name: "Light Blue",				rgb: "#ADD8E6",		h: 195,		s: 53,		l: 79,	],
		[ name: "Light Coral",				rgb: "#F08080",		h: 0,		s: 79,		l: 72,	],
		[ name: "Light Cyan",				rgb: "#E0FFFF",		h: 180,		s: 100,		l: 94,	],
		[ name: "Light Golden Rod Yellow",	rgb: "#FAFAD2",		h: 60,		s: 80,		l: 90,	],
		[ name: "Light Gray",				rgb: "#D3D3D3",		h: 0,		s: 0,		l: 83,	],
		[ name: "Light Green",				rgb: "#90EE90",		h: 120,		s: 73,		l: 75,	],
		[ name: "Light Pink",				rgb: "#FFB6C1",		h: 351,		s: 100,		l: 86,	],
		[ name: "Light Salmon",				rgb: "#FFA07A",		h: 17,		s: 100,		l: 74,	],
		[ name: "Light Sea Green",			rgb: "#20B2AA",		h: 177,		s: 70,		l: 41,	],
		[ name: "Light Sky Blue",			rgb: "#87CEFA",		h: 203,		s: 92,		l: 75,	],
		[ name: "Light Slate Gray",			rgb: "#778899",		h: 210,		s: 14,		l: 53,	],
		[ name: "Light Steel Blue",			rgb: "#B0C4DE",		h: 214,		s: 41,		l: 78,	],
		[ name: "Light Yellow",				rgb: "#FFFFE0",		h: 60,		s: 100,		l: 94,	],
		[ name: "Lime",						rgb: "#00FF00",		h: 120,		s: 100,		l: 50,	],
		[ name: "Lime Green",				rgb: "#32CD32",		h: 120,		s: 61,		l: 50,	],
		[ name: "Linen",					rgb: "#FAF0E6",		h: 30,		s: 67,		l: 94,	],
		[ name: "Maroon",					rgb: "#800000",		h: 0,		s: 100,		l: 25,	],
		[ name: "Medium Aquamarine",		rgb: "#66CDAA",		h: 160,		s: 51,		l: 60,	],
		[ name: "Medium Blue",				rgb: "#0000CD",		h: 240,		s: 100,		l: 40,	],
		[ name: "Medium Orchid",			rgb: "#BA55D3",		h: 288,		s: 59,		l: 58,	],
		[ name: "Medium Purple",			rgb: "#9370DB",		h: 260,		s: 60,		l: 65,	],
		[ name: "Medium Sea Green",			rgb: "#3CB371",		h: 147,		s: 50,		l: 47,	],
		[ name: "Medium Slate Blue",		rgb: "#7B68EE",		h: 249,		s: 80,		l: 67,	],
		[ name: "Medium Spring Green",		rgb: "#00FA9A",		h: 157,		s: 100,		l: 49,	],
		[ name: "Medium Turquoise",			rgb: "#48D1CC",		h: 178,		s: 60,		l: 55,	],
		[ name: "Medium Violet Red",		rgb: "#C71585",		h: 322,		s: 81,		l: 43,	],
		[ name: "Midnight Blue",			rgb: "#191970",		h: 240,		s: 64,		l: 27,	],
		[ name: "Mint Cream",				rgb: "#F5FFFA",		h: 150,		s: 100,		l: 98,	],
		[ name: "Misty Rose",				rgb: "#FFE4E1",		h: 6,		s: 100,		l: 94,	],
		[ name: "Moccasin",					rgb: "#FFE4B5",		h: 38,		s: 100,		l: 85,	],
		[ name: "Navajo White",				rgb: "#FFDEAD",		h: 36,		s: 100,		l: 84,	],
		[ name: "Navy",						rgb: "#000080",		h: 240,		s: 100,		l: 25,	],
		[ name: "Old Lace",					rgb: "#FDF5E6",		h: 39,		s: 85,		l: 95,	],
		[ name: "Olive",					rgb: "#808000",		h: 60,		s: 100,		l: 25,	],
		[ name: "Olive Drab",				rgb: "#6B8E23",		h: 80,		s: 60,		l: 35,	],
		[ name: "Orange",					rgb: "#FFA500",		h: 39,		s: 100,		l: 50,	],
		[ name: "Orange Red",				rgb: "#FF4500",		h: 16,		s: 100,		l: 50,	],
		[ name: "Orchid",					rgb: "#DA70D6",		h: 302,		s: 59,		l: 65,	],
		[ name: "Pale Golden Rod",			rgb: "#EEE8AA",		h: 55,		s: 67,		l: 80,	],
		[ name: "Pale Green",				rgb: "#98FB98",		h: 120,		s: 93,		l: 79,	],
		[ name: "Pale Turquoise",			rgb: "#AFEEEE",		h: 180,		s: 65,		l: 81,	],
		[ name: "Pale Violet Red",			rgb: "#DB7093",		h: 340,		s: 60,		l: 65,	],
		[ name: "Papaya Whip",				rgb: "#FFEFD5",		h: 37,		s: 100,		l: 92,	],
		[ name: "Peach Puff",				rgb: "#FFDAB9",		h: 28,		s: 100,		l: 86,	],
		[ name: "Peru",						rgb: "#CD853F",		h: 30,		s: 59,		l: 53,	],
		[ name: "Pink",						rgb: "#FFC0CB",		h: 350,		s: 100,		l: 88,	],
		[ name: "Plum",						rgb: "#DDA0DD",		h: 300,		s: 47,		l: 75,	],
		[ name: "Powder Blue",				rgb: "#B0E0E6",		h: 187,		s: 52,		l: 80,	],
		[ name: "Purple",					rgb: "#800080",		h: 300,		s: 100,		l: 25,	],
		[ name: "Red",						rgb: "#FF0000",		h: 0,		s: 100,		l: 50,	],
		[ name: "Rosy Brown",				rgb: "#BC8F8F",		h: 0,		s: 25,		l: 65,	],
		[ name: "Royal Blue",				rgb: "#4169E1",		h: 225,		s: 73,		l: 57,	],
		[ name: "Saddle Brown",				rgb: "#8B4513",		h: 25,		s: 76,		l: 31,	],
		[ name: "Salmon",					rgb: "#FA8072",		h: 6,		s: 93,		l: 71,	],
		[ name: "Sandy Brown",				rgb: "#F4A460",		h: 28,		s: 87,		l: 67,	],
		[ name: "Sea Green",				rgb: "#2E8B57",		h: 146,		s: 50,		l: 36,	],
		[ name: "Sea Shell",				rgb: "#FFF5EE",		h: 25,		s: 100,		l: 97,	],
		[ name: "Sienna",					rgb: "#A0522D",		h: 19,		s: 56,		l: 40,	],
		[ name: "Silver",					rgb: "#C0C0C0",		h: 0,		s: 0,		l: 75,	],
		[ name: "Sky Blue",					rgb: "#87CEEB",		h: 197,		s: 71,		l: 73,	],
		[ name: "Slate Blue",				rgb: "#6A5ACD",		h: 248,		s: 53,		l: 58,	],
		[ name: "Slate Gray",				rgb: "#708090",		h: 210,		s: 13,		l: 50,	],
		[ name: "Snow",						rgb: "#FFFAFA",		h: 0,		s: 100,		l: 99,	],
		[ name: "Spring Green",				rgb: "#00FF7F",		h: 150,		s: 100,		l: 50,	],
		[ name: "Steel Blue",				rgb: "#4682B4",		h: 207,		s: 44,		l: 49,	],
		[ name: "Tan",						rgb: "#D2B48C",		h: 34,		s: 44,		l: 69,	],
		[ name: "Teal",						rgb: "#008080",		h: 180,		s: 100,		l: 25,	],
		[ name: "Thistle",					rgb: "#D8BFD8",		h: 300,		s: 24,		l: 80,	],
		[ name: "Tomato",					rgb: "#FF6347",		h: 9,		s: 100,		l: 64,	],
		[ name: "Turquoise",				rgb: "#40E0D0",		h: 174,		s: 72,		l: 56,	],
		[ name: "Violet",					rgb: "#EE82EE",		h: 300,		s: 76,		l: 72,	],
		[ name: "Wheat",					rgb: "#F5DEB3",		h: 39,		s: 77,		l: 83,	],
		[ name: "White Smoke",				rgb: "#F5F5F5",		h: 0,		s: 0,		l: 96,	],
		[ name: "Yellow",					rgb: "#FFFF00",		h: 60,		s: 100,		l: 50,	],
		[ name: "Yellow Green",				rgb: "#9ACD32",		h: 80,		s: 61,		l: 50,	],
	]
}

private colorOptions() {
	return colors()*.name
}

private colorTempOpts(){
	return [
    	[ 1900: "1900K Candle" ],
        [ 2200: "2200K Hue Ambiance Minimum" ],
        [ 2400: "2400K Standard Incandescent" ],
        [ 2600: "2600K Soft White Incandescent" ],
        [ 3000: "3000K Warm White CFL" ],
        [ 3300: "3300K Studio Lamp" ],
        [ 4100: "4100K Moonlight" ],
        [ 4800: "4800K Direct Sun" ],
        [ 5500: "5500K Day White" ],
        [ 6500: "6500K Hue Ambiance Maximum" ],
        [ 7000: "7000K Cool White" ],
        [ 9000: "9000K Blue Sky" ]
   	]
}

/*****************************************************************/
/** Triggers
/*****************************************************************/

//Trigger for each child app type
def executeChild(variable){

	// If parent app then trigger last notification
    if(!parent) { repeatLastNotification(null) ;return}
	if(disabledCheck()){return}
    //Run based on type
	switch (settings.modType) {
       	case "Zone":			zMotionDetected("active"); zMotionDetected("inactive")	;break
        case "Scene":			executeScene()											;break
        case "MultiMedia":		mPlayerDTCommand("playing")								;break
        case "Notification":	executeNotification(variable)							;break
        case "Security":		executeSecurityMode(variable)							;break
        return
	}
}

def routineChanged(evt){
	// Get ID of triggered routine
	location.helloHome?.getPhrases().each { routine ->
        if(evt.displayName == routine.label && routine.id == settings?.triggerRoutine){
        	logWriter("Event triggered by routine change")
    		executeChild(null)
        }     
	}
}

// Send an event to a child app
def triggerChild(cID, variable) {
	def children = getChildApps()
	children.each { child ->
    	if(child.id == cID) {
    		logWriter("Found and triggered child: $child.label")
    		child.executeChild(variable)
		}
	}
}

// Remote Control Trigger
def controlTrigger(params){
    triggerChild(params.cid,null)
    pageMainControl()
}

// LAST NOTIFICATION
def setLastNotification(app, value){
	state.lastNotificationApp = app?.value as String
    state.lastNotificationVal = value?.value as String
}

def repeatLastNotification(evt){
	// exit if no value
    if(!state?.lastNotificationApp){return}
    logWriter ("Last Notification Repeated FOR: $state.lastNotificationApp SENDING: $state.lastNotificationVal")
    // run event
	triggerChild(state.lastNotificationApp, state.lastNotificationVal)
}

// Manual trigger from in thte app
def manualTrigger(){
    executeChild()
    pageAdvanced()
}

// EXIT if disabled
def disabledCheck(){
	if(settings?.disabled != null && disabled.switchState.value == "on") { logWriter("Disabled by switch on"); return true }
    if(settings?.disabledOff != null && disabledOff.switchState.value == "off") { logWriter("Disabled by switch off"); return true }
    def timeMode = parent.getTimeMode()
    if (settings?.activeMode != null && !activeMode.contains("Any") && !activeMode.contains(timeMode)) { logWriter("Disabled by invalid mode"); return true }
    if (settings?.activeSecMode != null && !activeSecMode.contains("Any") && !activeSecMode.contains(securityState("get"))) { logWriter("Disabled by invalid security mode"); return true }
    return false
}

// RECURRING CHECKS - To ensure recurring checks keeps running triggers by sunrise, sunset and App Update
def keepAlive(){
	recurringChecks()
	runEvery15Minutes(recurringChecks)
}

def recurringChecks(){
	logWriter("Checking in for my regular check")
    setSTMode()
    executeVacation()
}

// LOGGING
def updateLog(command, name, length, event){
    
    def logName = "log$name" as String									// Add log prefix
    if(settings?.length == null || length == 0){state.remove(logName); return "State Cleared"}		// If length set to 0, delete state
	if(!state."$logName"){state."$logName" = []}						// If list state new, create blank list
	def tempList = state."$logName"										// Create a temp List
    
    switch(command) {    
       	case "set":
        	if(!length || tempList.size() < length){length = tempList.size()+1}	// Get valid trim length if short
            tempList.add(0,"${new Date(now()).format("dd MMM HH:mm", location.timeZone)} - ${event}") // Add to top of tempList
            state."$logName" = tempList.subList(0, length)
        break;
        case "get":
        	if(!length || tempList.size() < length){length = tempList.size()}	// Get valid trim length if short
        	def formattedList = ""
            tempList = tempList.subList(0, length)
            tempList.each { item ->
            	if(formattedList == ""){
                	formattedList = item
                }else{
            		formattedList = formattedList + "\n" + item
                }
            }
            return formattedList
        break;
    }
}

/*****************************************************************/
/** appLink Code
/*****************************************************************/

// appLink core code: This should be included in every appLink compatible app, and not customised.
def appLinkHandler(evt){
    if(!state.appLink) state.appLink = [:]
    switch(evt.value) { //[appLink V0.0.2 2016-12-08]
   		case "add":	state.appLink << evt.jsonData;	break;
        case "del":	state.appLink.remove(evt.jsonData.app);	break;             
        case "list":	def list = [:];	state.appLink.each {key, value -> value.each{skey, svalue -> list << ["${key}:${skey}" : "[${key}] ${svalue}"]}};
        	return list.sort { a, b -> a.value.toLowerCase() <=> b.value.toLowerCase() };	break;
        case "run":	sendLocationEvent(name: "${evt.data.split(":")[0]}", value: evt.data.split(":")[1] , isStateChange: true, descriptionText: "appLink run"); break;
    }
    state.appLink.remove("$app.name") // removes this app from list
}

// Get and push list of child apps to appLink using ID as the key - this avoids issues if an app label is re-named
def sendChildAppLink(){
	def result = [:]
	childApps.each { child ->
		if(appLinkListSelect.contains(child.id)){result << ["${child.id as String}" : "${child.appName as String}"]}
	}
    result << ["repeatNotification" : "Repeat Notification"]
	result.sort { a, b -> a.value.toLowerCase() <=> b.value.toLowerCase() }
    
    // # DO NOT EDIT BELOW #
    // Create submap with key as app name, then ask for a refresh of all clients
	def list = [:]; list << ["$app.name" : result]
    sendLocationEvent(name: "appLink", value: "add" , isStateChange: true, descriptionText: "appLink Add", data: list)
}

// Execute action from App Link
def appLinkExecute(evt){
	if(evt.value == "repeatNotification") {repeatLastNotification(); return}
	triggerChild(evt.value, null)
}


/* DEV SPACE



*/