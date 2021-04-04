metadata {
    definition (name: "Danfoss Hydronic Controller", namespace: "datanordic", author: "Hans Andersen <hans@dueandersen.com>") {
        capability("Actuator")
        capability("Temperature Measurement")
        capability("Thermostat")
        capability("Thermostat Heating Setpoint")
        capability("Thermostat Cooling Setpoint")
        capability("Thermostat Operating State")
        capability("Thermostat Mode")
        capability("Thermostat Fan Mode")
        capability("Refresh")
        capability("Sensor")
        capability("Health Check")

        fingerprint(zw:"L", type:"0806", mfr:"0002", prod:"0002", model:"4005", ver:"1.00", zwv:"3.67", lib:"03", cc:"85,59,70,5A,87,72,60,8E,71,25,43,40,86", ccOut:"31", epc:"7", deviceJoinName:"Danfoss Hydronic Controller")
    }

    preferences {
        if(!parent) { // root
            input(name: "valveType", type: "enum", title: "Valve type", required: true, options: ["closed": "Closed", "open": "Open"], defaultValue: "closed")
            input(name: "heatLoadStrategy", type: "enum", title: "Heat load strategy", required: true, options: ["stacking": "Stacking", "spreading": "Spreading"], defaultValue: "stacking")
        }

        input(name: "pwmPeriod", type: "enum", title: "PWM Period", description: null, required: true, options:["short": "Short (15 min)", "medium": "Medium (30 min)", "long": "Long (60 min)"], defaultValue: "long")
    }

    tiles {
        multiAttributeTile(name:"temperature", type:"generic", width:3, height:2, canChangeIcon: true) {
            tileAttribute("device.temperature", key: "PRIMARY_CONTROL") {
                attributeState("temperature", label:'${currentValue}°', icon: "st.alarm.temperature.normal",
                        backgroundColors:[
                                // Celsius
                                [value: 0, color: "#153591"],
                                [value: 7, color: "#1e9cbb"],
                                [value: 15, color: "#90d2a7"],
                                [value: 23, color: "#44b621"],
                                [value: 28, color: "#f1d801"],
                                [value: 35, color: "#d04e00"],
                                [value: 37, color: "#bc2323"],
                                // Fahrenheit
                                [value: 40, color: "#153591"],
                                [value: 44, color: "#1e9cbb"],
                                [value: 59, color: "#90d2a7"],
                                [value: 74, color: "#44b621"],
                                [value: 84, color: "#f1d801"],
                                [value: 95, color: "#d04e00"],
                                [value: 96, color: "#bc2323"]
                        ]
                )
            }
        }
        standardTile("mode", "device.thermostatMode", width:2, height:2, inactiveLabel: false, decoration: "flat") {
            state "off", action:"switchMode", nextState:"...", icon: "st.thermostat.heating-cooling-off"
            state "heat", action:"switchMode", nextState:"...", icon: "st.thermostat.heat"
            state "cool", action:"switchMode", nextState:"...", icon: "st.thermostat.cool"
            state "auto", action:"switchMode", nextState:"...", icon: "st.thermostat.auto"
            state "emergency heat", action:"switchMode", nextState:"...", icon: "st.thermostat.emergency-heat"
            state "...", label: "Updating...",nextState:"...", backgroundColor:"#ffffff"
        }
        standardTile("fanMode", "device.thermostatFanMode", width:2, height:2, inactiveLabel: false, decoration: "flat") {
            state "auto", action:"switchFanMode", nextState:"...", icon: "st.thermostat.fan-auto"
            state "on", action:"switchFanMode", nextState:"...", icon: "st.thermostat.fan-on"
            state "circulate", action:"switchFanMode", nextState:"...", icon: "st.thermostat.fan-circulate"
            state "...", label: "Updating...", nextState:"...", backgroundColor:"#ffffff"
        }
        standardTile("lowerHeatingSetpoint", "device.heatingSetpoint", width:2, height:1, inactiveLabel: false, decoration: "flat") {
            state "heatingSetpoint", action:"lowerHeatingSetpoint", icon:"st.thermostat.thermostat-left"
        }
        valueTile("heatingSetpoint", "device.heatingSetpoint", width:2, height:1, inactiveLabel: false, decoration: "flat") {
            state "heatingSetpoint", label:'${currentValue}° heat', backgroundColor:"#ffffff"
        }
        standardTile("raiseHeatingSetpoint", "device.heatingSetpoint", width:2, height:1, inactiveLabel: false, decoration: "flat") {
            state "heatingSetpoint", action:"raiseHeatingSetpoint", icon:"st.thermostat.thermostat-right"
        }
        standardTile("lowerCoolSetpoint", "device.coolingSetpoint", width:2, height:1, inactiveLabel: false, decoration: "flat") {
            state "coolingSetpoint", action:"lowerCoolSetpoint", icon:"st.thermostat.thermostat-left"
        }
        valueTile("coolingSetpoint", "device.coolingSetpoint", width:2, height:1, inactiveLabel: false, decoration: "flat") {
            state "coolingSetpoint", label:'${currentValue}° cool', backgroundColor:"#ffffff"
        }
        standardTile("raiseCoolSetpoint", "device.heatingSetpoint", width:2, height:1, inactiveLabel: false, decoration: "flat") {
            state "heatingSetpoint", action:"raiseCoolSetpoint", icon:"st.thermostat.thermostat-right"
        }
        standardTile("thermostatOperatingState", "device.thermostatOperatingState", width: 2, height:1, decoration: "flat") {
            state "thermostatOperatingState", label:'${currentValue}', backgroundColor:"#ffffff"
        }
        standardTile("refresh", "device.thermostatMode", width:2, height:1, inactiveLabel: false, decoration: "flat") {
            state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        main "temperature"
        details(["temperature", "lowerHeatingSetpoint", "heatingSetpoint", "raiseHeatingSetpoint", "lowerCoolSetpoint",
                 "coolingSetpoint", "raiseCoolSetpoint", "mode", "fanMode", "thermostatOperatingState", "refresh"])
    }
}

def installed() {
    // Configure device
    log.debug("Installing ${device}")

    endpointCommands([
            zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:[zwaveHubNodeId]),
            zwave.manufacturerSpecificV2.manufacturerSpecificGet()
    ])

    if(!parent) {
        createChildThermostatDevices()
        createChildPowerSwitchDevices()
    }

    runIn(3, "initialize", [overwrite: true])
}

def poll() {
    log.debug("Poll ${device}")

    def cmds = endpointCommands([
            zwave.thermostatModeV2.thermostatModeSupportedGet(),
            zwave.thermostatModeV2.thermostatModeGet(),
            zwave.thermostatSetpointV1.thermostatSetpointGet(setpointType: 1),
            zwave.configurationV1.configurationGet(parameterNumber: 3),
    ])

    if(!parent) { // root
        cmds += commands([
                zwave.configurationV1.configurationGet(parameterNumber: 1),
                zwave.configurationV1.configurationGet(parameterNumber: 2),
        ])
    }

    sendCmd(cmds)
}

def refresh() {
    poll()
}

def updated() {
    def cmds = []

    if(!parent) { // root
        if(valveType) {
            cmds << command(zwave.configurationV1.configurationSet(parameterNumber: 1, scaledConfigurationValue: ["closed": 0x00, "open": 0x01][valveType]))
        }
        if(heatLoadStrategy) {
            cmds << command(zwave.configurationV1.configurationSet(parameterNumber: 2, scaledConfigurationValue: ["stacking": 0x00, "spreading": 0x01][heatLoadStrategy]))
        }
    }

    cmds << endpointCommand(zwave.configurationV1.configurationSet(parameterNumber: 3, scaledConfigurationValue: ["short": 0x00, "medium": 0x01, "long": 0x02][pwmPeriod]))

    sendCmd(cmds)

    // If not set update ManufacturerSpecific data
    if (!getDataValue("manufacturer")) {
        sendCmd(endpointCommand(zwave.manufacturerSpecificV2.manufacturerSpecificGet()))
        runIn(2, "initialize", [overwrite: true])  // Allow configure command to be sent and acknowledged before proceeding
    } else {
        initialize()
    }
}

def initialize() {
    // Device-Watch simply pings if no device events received for 32min(checkInterval)
    sendEvent(name: "checkInterval", value: 2 * 15 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])

    unschedule()
    runEvery5Minutes("poll")

    poll()
}

private void createChildThermostatDevices() {
    for (i in 1..4) {
        def n = i + 1
        addChildDevice("Danfoss Hydronic Controller",
                "${device.deviceNetworkId}-${i}",
                null,
                [
                        completedSetup: true,
                        label: "${device.displayName} (Thermostat ${n})",
                        isComponent: false,
                        componentName: "th$n",
                        componentLabel: "Thermostat $n"
                ])
    }
}

private void createChildPowerSwitchDevices() {
    // Add child devices for all two power swtches of Danfoss Hydronic Controller
    for (n in 1..2) {
        def i = n + 4
        addChildDevice("smartthings",
                "Child Switch",
                "${device.deviceNetworkId}-${i}",
                null,
                [
                        completedSetup: true,
                        label: "${device.displayName} (Power Switch ${n})",
                        isComponent: false,
                        componentName: "ps$n",
                        componentLabel: "Power Switch $n"
                ])
    }
}

// Power Switch
void childOn(String dni) {
    sendHubCommand(encap(switchBinaryV1.SwitchBinarySet(switchValue: 0xFF), getChildEndpoint(dni)))
}

void childOff(String dni) {
    sendHubCommand(encap(switchBinaryV1.SwitchBinarySet(switchValue: 0x00), getChildEndpoint(dni)))
}

// Thermostat
def open() {
    log.debug("Open valve ${device.displayName}")

    sendCmd(endpointCommands([
            zwave.thermostatModeV2.thermostatModeSet(mode: 0x01),
            zwave.thermostatSetpointV2.thermostatSetpointSet(precision: 2, scale: 0, scaledValue: 30.00, setpointType: 1, size: 2),
            zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 1),
    ]))
}

def close() {
    log.debug("Close valve ${device.displayName}")

    sendCmd(endpointCommands([
            zwave.thermostatModeV2.thermostatModeSet(mode: 0x00),
            zwave.thermostatSetpointV1.thermostatSetpointSet(precision: 2, scale: 0, scaledValue: 10.00, setpointType: 1, size: 2),
            zwave.thermostatSetpointV2.thermostatSetpointGet(setpointType: 1),
    ]))
}

def ping() {
    log.debug("Ping ${device.displayName}")

    sendCmd(endpointCommand(zwave.indicatorV1.indicatorSet(value: 0xFF)))
}

def parse(String description) {
    def result = null

    if (description != "updated") {
        //log.debug("ZWave: ${description}")

        def zwcmd = zwave.parse(description, [0x43: 1, 0x70: 1])

        if (zwcmd) {
            result = zwaveEvent(zwcmd)
        } else {
            log.debug "$device.displayName couldn't parse $description"
        }
    }

    if (!result) {
        return []
    }

    return [result]
}

// Event Generation
def zwaveEvent(physicalgraph.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
    def encapsulatedCommand = cmd.encapsulatedCommand([0x43: 1, 0x70: 1])
    def child = cmd.sourceEndPoint - 1

    if (cmd.sourceEndPoint == 1) {
        zwaveEvent(encapsulatedCommand)
    } else {
        childDevices[child]?.zwaveEvent(encapsulatedCommand)
        [:]
    }
}

def zwaveEvent(physicalgraph.zwave.commands.thermostatmodev2.ThermostatModeReport cmd) {
    def options = ["0": "closed", "1": "open"]

    sendEvent(name: "valve", value: options[cmd.mode.toString()], displayed: true)
}

def zwaveEvent(physicalgraph.zwave.commands.thermostatmodev2.ThermostatModeSupportedReport cmd) {
    def supportedModes = []
    if(cmd.off) { supportedModes << "off" }
    if(cmd.heat) { supportedModes << "heat" }

    sendEvent(name: "supportedThermostatModes", value: supportedModes, displayed: false)
}

def zwaveEvent(physicalgraph.zwave.commands.thermostatsetpointv1.ThermostatSetpointReport cmd) {
    log.debug("Thermostat setpoint type: ${cmd.setpointType} value: ${cmd.scaledValue}")
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {
    if(cmd.parameterNumber == 1) {
        def options = ["0": "closed", "1": "open"]
        sendEvent(name: "valveType", value: options[cmd.scaledConfigurationValue.toString()], displayed: false)
    }
    if(cmd.parameterNumber == 2) {
        def options = ["0": "stacking", "1": "spreading"]
        sendEvent(name: "heatLoadStrategy", value: options[cmd.scaledConfigurationValue.toString()], displayed: false)
    }
    if(cmd.parameterNumber == 3) {
        def options = ["0": "short", "1": "medium", "2": "long"]
        sendEvent(name: "pwmPeriod", value: options[cmd.scaledConfigurationValue.toString()], displayed: false)
    }
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
    if (cmd.manufacturerName) {
        updateDataValue("manufacturer", cmd.manufacturerName)
    }

    if (cmd.productTypeId) {
        updateDataValue("productTypeId", cmd.productTypeId.toString())
    }

    if (cmd.productId) {
        updateDataValue("productId", cmd.productId.toString())
    }

    log.debug("ManufacturerSpecificReport: $cmd")
}

def zwaveEvent(physicalgraph.zwave.commands.notificationv3.NotificationReport cmd) {
    log.debug("Notification ${cmd}")

    if(cmd.notificationType == 0x0F) {
        sendCmd(command(zwave.thermostatModeV2.thermostatModeGet()))
    }
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
    log.debug("Zwave BasicReport: $cmd")
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
    log.warn "Unexpected zwave command $cmd"
    return createEvent(descriptionText: "${device.displayName}: ${cmd}")
}

private getChildEndpoint(String dni) {
    def matcher = (dni =~ /^[0-9A-Z]+-(\d+)$/)
    return matcher[0][1].toInteger() + 1
}

private getEndpoint() {
    return parent ? getChildEndpoint(device.deviceNetworkId) : 1
}

private command(physicalgraph.zwave.Command cmd) {
    if (state.sec) {
        return zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
    } else {
        return cmd.format()
    }
}

private commands(commands, delay=200) {
    return delayBetween(commands.collect{ command(it) }, delay)
}

private encap(physicalgraph.zwave.Command cmd, endpoint) {
    try {
        if (endpoint) {
            return command(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint:endpoint).encapsulate(cmd))
        } else {
            return command(cmd)
        }
    }
    catch(e) {
        log.warn(e)
        log.debug(cmd)
    }
}

private encap(cmd, endpoint) {
    log.debug(cmd)
}

private endpointCommand(physicalgraph.zwave.Command cmd) {
    return encap(cmd, getEndpoint())
}

private endpointCommands(commands, delay=200) {
    return delayBetween(commands.collect{ endpointCommand(it) }, delay)
}

private encapWithDelay(commands, endpoint, delay=200) {
    return delayBetween(commands.collect{ encap(it, endpoint) }, delay)
}

def sendCmd(commands) {
    if(parent) {
        return parent.sendCmd(commands)
    }
    else {
        return sendHubCommand(commands)
    }
}
