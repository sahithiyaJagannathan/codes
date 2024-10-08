import csv
import docker
import os
import subprocess


data = []
path = 'unix:///Users/lawrance-13865/.rd/docker.sock'
dockerClient = docker.DockerClient(base_url=path)
scriptPath = '/build/ZOHODB/output/AdventNet/Sas/tomcat/bin'

def isContainerAlreadyCreated(containerName):
    containers = dockerClient.containers.list(all=True)
    for container in containers:
        if container.name == containerName:
            print("Container already created")
            return container
    return None

def isContainerIsRunning(container):
    container.reload()
    state = container.attrs["State"]
    return state["Running"]

def createDockerContainer(buildPath, containerName, dataName):
    print("Creating container {containerName}".format(containerName=containerName))
    volumes = ['{dataName}:/home/sas/mysql/data'.format(dataName=dataName), 
                '{buildPath}/build/ZOHODB/output/AdventNet/Sas/tomcat/qtabletemp:{buildPath}/zohoreports/build/ZOHODB/output/AdventNet/Sas/tomcat/qtabletemp'
                .format(buildPath=buildPath)]
    container = dockerClient.containers.run(image="centos-za-db-components-all-latest:latest", command=["redis", "mysql", "terminal"], detach="True", volumes=volumes, name=containerName, network_mode="host", stdin_open=True, tty=True)
    return container

def initJBossDB(containerName):
    container = dockerClient.containers.get(containerName)
    container.exec_run(workdir="/home/sas/mysql/bin", cmd="./mysql -u root -se 'create database jbossdb'")
    print("Created jbossdb")

with open("./repos.txt", 'r') as file:
    csvreader = csv.reader(file)
    for row in csvreader:
        dataDict = {'path': row[0], 'containerName': row[1], 'dataName': row[2]}
        data.append(dataDict)
    

print("Select the build to start")
for index, value in enumerate(data):
    print('{index}. {path}'.format(index=index+1, path=value['path']))

userInput = int(input("Input: "))

buildToStart = data[userInput - 1]

container = isContainerAlreadyCreated(buildToStart["containerName"])

if container == None:
    container = createDockerContainer(containerName=buildToStart["containerName"], buildPath=buildToStart["path"], dataName=buildToStart["dataName"])
    initJBossDB(buildToStart["containerName"])

if not isContainerIsRunning(container):
    print("Starting Container {}", container.name)
    container.start()

scriptPath = buildToStart["path"] + scriptPath

os.chdir(scriptPath)
subprocess.call(['sh', 'mac_run.sh'])
