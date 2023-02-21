package edu.sjsu.cs185c.didyousee;

import io.grpc.Grpc;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.awt.image.ImagingOpException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.LinkedList;

public class Main {

    private static String myName = "";
    private static int epoch = 0;
    private static String myPort = "";
    private static List<GrpcHello.GossipInfo> list = new LinkedList<>();
    private static GrpcHello.GossipInfo myInfoInTheList = null;

    private static void replace() {
        var temp = GrpcHello.GossipInfo.newBuilder().setHostPort(myPort).setName(myName).setEpoch(epoch).build();
        for(int i = 0; i < list.size(); i++) {
            var curr = list.get(i);
            if(curr.getName().equals(temp.getName()) && curr.getHostPort().equals(temp.getHostPort())) {
                list.remove(i);
                list.add(temp);
                break;
            }
        }
    }

    public static int getEpoch(String hostPort) {
        for(GrpcHello.GossipInfo a : list) {
            if(a.getHostPort() == hostPort) {
                return a.getEpoch();
            }
        }
        return -1;
    }

    private static List<GrpcHello.GossipInfo> listIntersection(List<GrpcHello.GossipInfo> a, List<GrpcHello.GossipInfo> b) {
        LinkedList<GrpcHello.GossipInfo> link = new LinkedList<>();
        link.addAll(a);
        int tempEpoch = epoch;
        for(GrpcHello.GossipInfo info : b) {
            tempEpoch = Math.max(tempEpoch, info.getEpoch());
            boolean found = false;
            for(int i = 0; i < link.size(); i++) {
                if(info.getHostPort().equals(link.get(i).getHostPort())
                        && info.getName().equals(link.get(i).getName())) {
                    found = true;
                    if(info.getEpoch() > link.get(i).getEpoch()) {
                        link.remove(i);
                        link.add(info);
                        break;
                    }
                }
            }
            if(!found) {
                link.add(info);
            }
        }
        replace();
        return link;
    }

    private static void sendCall(String hostPort) {
        try {
            System.out.println("SENDING CALL TO " + hostPort);
            var channel =
                    ManagedChannelBuilder.forTarget(hostPort).usePlaintext().build();
            var stub = WhosHereGrpc.newBlockingStub(channel).withDeadlineAfter(3, TimeUnit.SECONDS);
            var request = GrpcHello.GossipRequest.newBuilder().addAllInfo(list).build();
            var response = stub.gossip(request);
            if(response.getInfoList().size() == 1) {
                if(response.getInfoList().get(0).getEpoch() == 0) {
                    epoch++;
                    replace();
                }
            }
            list = listIntersection(response.getInfoList(), list);
        } catch (io.grpc.StatusRuntimeException e) {
            System.out.println("CALL FAILED TO " + hostPort);
            changed = true;
            if(epoch == getEpoch(hostPort)) {
                epoch++;
                replace();
            }
        }
    }

    private static void sortList() {
        List<GrpcHello.GossipInfo> newList = new LinkedList<>();
        for(int i = 0; i < list.size(); i++) {
            if(list.get(i) == null) {
                list.remove(i);
                i--;
                continue;
            }
            if(list.get(i).getName().equals("Cary") && list.get(i).getHostPort().equals("myPort")) {
                newList.add(list.get(i));
                list.remove(i);
                break;
            }
        }
        list.sort(new Comparator<GrpcHello.GossipInfo>() {
            @Override
            public int compare(GrpcHello.GossipInfo o1, GrpcHello.GossipInfo o2) {
                if(o1 == null) return -1;
                if(o2 == null) return 1;
                return o1.getHostPort().compareTo(o2.getHostPort());
            }
        });
        newList.addAll(list);
        list = newList;
    }

    private static void printList() {
        System.out.println("=======");
        if(!(list.size() < 2)) {
            list.sort((o1, o2) -> {
                if(o1 == null) return -1;
                if(o2 == null) return 1;
                if(o1.getEpoch() != o2.getEpoch()) {
                    if(o1.getEpoch() > o2.getEpoch()) return 1;
                    return -1;
                }
                return o1.getHostPort().compareTo(o2.getHostPort());
            });
        }
        for(GrpcHello.GossipInfo info : list) {
            if(info.getEpoch() > epoch) {
                epoch = info.getEpoch();
                myInfoInTheList = GrpcHello.GossipInfo.newBuilder().setEpoch(epoch).setHostPort(myPort).setName(myName).build();
            }
            System.out.println(String.valueOf(info.getEpoch()) + " " + info.getHostPort() + " " + info.getName());
        }
        replace();
    }

    @Command(name = "hello", subcommands = {CliClient.class, CliServer.class})
    static class TopCommand {
    }
    @Command(name = "helloClient", mixinStandardHelpOptions = true, description = "Gossip Clent.")
    static class CliClient implements Callable<Integer> {
        @Parameters(index = "0", description = "name")
        String name;

        @Parameters(index = "1", description = "ip")
        String myPort;

        @Parameters(index = "2..*", description = "server to connect to.")
        String[] serverPorts;

        @Override
        public Integer call() throws Exception {
            String serverPort = "";
            try {
                for (String a : serverPorts) {
                    serverPort = a;
                    System.out.printf("will contact %s\n", serverPort);
                    var channel =
                            ManagedChannelBuilder.forTarget(serverPort).usePlaintext().build();
                    System.out.println("channel" + channel);
                    System.out.println(channel.getState(true));
                    System.out.println("-----");
                    var stub = WhosHereGrpc.newBlockingStub(channel);
                    var request = GrpcHello.GossipRequest.newBuilder().addAllInfo(list).build();
                    var response = stub.gossip(request);
                    System.out.println("Call successful to host " + serverPort);
                    list = listIntersection(response.getInfoList(), list);
                    printList();
                }
            } catch(io.grpc.StatusRuntimeException e){
                System.out.println("Call failed to host " + serverPort);
                changed = true;
            }
            return 0;
        }
    }

    static class HelloImpl extends WhosHereGrpc.WhosHereImplBase {
        @Override
        public void gossip(GrpcHello.GossipRequest request, StreamObserver<GrpcHello.GossipResponse> responseObserver) {
            var response = GrpcHello.GossipResponse.newBuilder().addAllInfo(list).setRc(0).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            if(request.getInfoList().size() == 1) {
                if(request.getInfoList().get(0).getEpoch() == 0) {
                    epoch++;
                    replace();
                }
            }
            list = listIntersection(request.getInfoList(), list);
        }

        @Override
        public void whoareyou(GrpcHello.WhoRequest request, StreamObserver<GrpcHello.WhoResponse> responseObserver) {
            System.out.println(request);
            var response = GrpcHello.WhoResponse.newBuilder().addAllInfo(list).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
    @Command(name = "helloServer", mixinStandardHelpOptions = true, description = "Gossip Server.")
    static class CliServer implements Callable<Integer> {
        @Parameters(index = "0", description = "name")
        String name;

        @Parameters(index = "1", description = "ip")
        String port;

        @Parameters(index = "2..*", description = "server to connect to.")
        String[] serverPorts;


        @Override
        public Integer call() throws Exception {
            if(serverPorts[0].equals("none")) {
                epoch = 1;
            }
            var getPort = port.split(":");
            var server = ServerBuilder.forPort(Integer.parseInt(getPort[1])).addService(new HelloImpl()).build();
            myPort = port;
            myName = name;
            server.start();
            GrpcHello.GossipInfo caryInfo = GrpcHello.GossipInfo.newBuilder().setName(myName).setHostPort(myPort).setEpoch(epoch).build();
            list.add(caryInfo);
            myInfoInTheList = caryInfo;
            for(String serverPort : serverPorts) {
                if(serverPort.equals("none")) continue;
                sendCall(serverPort);
            }
            boolean condition = true;
            while(condition) {
                if(myInfoInTheList != null) {
                    myInfoInTheList = GrpcHello.GossipInfo.newBuilder().setName(myName).setHostPort(myPort).setEpoch(epoch).build();
                }
                printList();
                Thread.sleep(5000);
                changed = false;
                sortList();
                boolean callOne = false;
                boolean callTwo = false;
                String portOne = "";
                String portTwo = "";
                if(list.size() >= 1) {
                    portOne = list.get(0).getHostPort();
                    callOne = true;
                }
                if(list.size() >= 2) {
                    portTwo = list.get(1).getHostPort();
                    callTwo = true;
                }
                boolean temp = false;
                if(list.size() >= 3 && portOne.equals(myPort)) {
                    portOne = list.get(2).getHostPort();
                    temp = true;
                }
                if(list.size() >= 3 && portTwo.equals(myPort)) {
                    if(temp && list.size() >= 4) {
                        portTwo = list.get(3).getHostPort();
                    } else {
                        portTwo = list.get(2).getHostPort();
                    }
                }
                if(portOne.equals(portTwo)) {
                    callTwo = false;
                }
                if(callOne || callTwo) {
                    if(portOne.equals(myPort)) {
                        callOne = false;
                    }
                    if(portTwo.equals(myPort)) {
                        callTwo = false;
                    }
                }
                if(callOne) {
                    sendCall(portOne);
                }
                if(callTwo) {
                    sendCall(portTwo);
                }
            }
            server.awaitTermination();
            return 0;
        }
    }
    public static void main(String[] args) {
        System.exit(new CommandLine(new TopCommand()).execute(args));
    }
}