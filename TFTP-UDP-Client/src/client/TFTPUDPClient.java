/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package client;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Scanner;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author 236197
 */
public class TFTPUDPClient {

    //op code
    private final byte RRQ = 1;
    private final byte WRQ = 2;
    private final byte DATA = 3;
    private final byte ACK = 4;
    private final byte ERROR = 5;
    
    private byte[] sendData = new byte[516];
    private byte[] receiveData = new byte[516];
    private byte[] block;
    
    private DatagramPacket sendPacket;
    private DatagramPacket receivePacket;
    
    private DatagramSocket clientSocket = null;
    private InetAddress IPAddress = null;
    
    String fileName = "randomText.txt";
    String mode = "octet";
    
    public static void main(String[] args) throws Exception {
        TFTPUDPClient start = new TFTPUDPClient();
        start.menu();
    }

    /**
     * method to display menu
     *
     * @throws java.io.IOException
     */
    private void menu() throws IOException {
        
        System.out.println("Please input file name");
        Scanner s1 = new Scanner(System.in);
        fileName = s1.nextLine();
        
        clientSocket = new DatagramSocket();
        IPAddress = InetAddress.getByName("localhost");
        
        System.out.println("please select one of the options below");
        System.out.println("1. Read File");
        System.out.println("2. Write File");
        System.out.println("0. Exit");
        System.out.println("");
        
        Scanner s = new Scanner(System.in);
        String input = s.nextLine();
        
        switch (input) {
            case "1":
                sendRRQ();
                break;
            case "2":
                sendWRQ();
                break;
            case "0":
                System.out.println("Goodbye");
                clientSocket.close();
                break;
            default:
                System.out.println("Command not recognised");
                break;
        }
    }

    /**
     * method to send RRQ and read
     *
     * @throws IOException
     */
    private void sendRRQ() throws IOException { //read
        sendData = request(RRQ);
        sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, 9001);
        clientSocket.send(sendPacket);
        System.out.println("RRQ sent.");
        processReceived();
        clientSocket.close();
    }

    /**
     * method to send WRQ and write to server after receiving acknowledgment
     *
     * @throws IOException
     */
    private void sendWRQ() throws IOException {
        sendData = request(WRQ); //send WRQ
        sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, 9001);
        clientSocket.send(sendPacket);
        System.out.println("WRQ sent.");
        processReceived();
        
    }
    
    private void sendData() throws IOException {
        
        try {
            byte[] blockNum = {0, 1};
            byte[] fileText = Files.readAllBytes(Paths.get(fileName));
            byte[] dataMSG = {0, DATA, blockNum[0], blockNum[1]};
            sendData = new byte[fileText.length + dataMSG.length];

            //combine DATA packet header with file
            System.arraycopy(dataMSG, 0, sendData, 0, dataMSG.length);
            System.arraycopy(fileText, 0, sendData, dataMSG.length, fileText.length);
            
            sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, 9001);
            clientSocket.send(sendPacket);
            System.out.println("DATA sent.");
            blockNum[1]++;
        } catch (FileNotFoundException e) {
            sendError(e.getMessage());
        }
    }

    /**
     * method to create RRQ and WRQ
     *
     * @param opcode
     * @return
     * @throws java.io.IOException
     */
    private byte[] request(byte opcode) throws IOException {
        int requestArrayLength = 2 + fileName.getBytes().length + 1 + mode.getBytes().length + 1;
        ByteArrayOutputStream os = new ByteArrayOutputStream(requestArrayLength);
        os.write(0);
        os.write(opcode);
        os.write(fileName.getBytes());
        os.write(0);
        os.write(mode.getBytes());
        os.write(0);
        byte[] requestArray = os.toByteArray();
        return requestArray;
    }

    /**
     * method to process received data
     *
     * @throws java.io.IOException
     */
    private void processReceived() throws IOException {
        
        while (true) {
            clientSocket.setSoTimeout(10000);
            receivePacket = new DatagramPacket(receiveData, receiveData.length);
            clientSocket.receive(receivePacket);
            
            byte[] sortOpCode = {receiveData[0], receiveData[1]};
            byte[] block = {receiveData[2], receiveData[3]}; //check block number
            if (sortOpCode[1] == DATA) {
                System.out.println("Data pack received");
                sendACK(block); //send ack to server for received packet

                String fileText = new String(receivePacket.getData());
                System.out.println("FROM SERVER: " + fileText);
                
                try {
                    FileOutputStream fos = new FileOutputStream(fileName);
                    fos.write(receivePacket.getData(), 4, receivePacket.getLength() - 4);
                    fos.close();
                } catch (FileNotFoundException e) {
                    sendError(e.getMessage());
                }
//                if (checkLastPacket(receivePacket) == false){
//                    clientSocket.close();
//                }

            } else if (sortOpCode[1] == ERROR) {
                String errorMSG = new String(receiveData, 4, receiveData.length - 4);
                System.out.println("Error message: " + errorMSG);
                
            } else if ((sortOpCode[1] == ACK)) {
                System.out.println("ACK received");
                sendData();
            }
            
        }
    }

    /**
     * method to send an error message, fixed to file not found error as per
     * instructions
     *
     * @param e
     * @throws java.io.IOException
     */
    private void sendError(String e) throws IOException {
        
        int errorArrayLength = 2 + 2 + e.length() + 1;
        ByteArrayOutputStream ebaos = new ByteArrayOutputStream(errorArrayLength);
        ebaos.write(0);
        ebaos.write(ERROR);
        ebaos.write(0);
        ebaos.write(1);
        ebaos.write(e.getBytes());
        ebaos.write(0);
        byte[] errorArr = ebaos.toByteArray();
        
        DatagramPacket dp = new DatagramPacket(errorArr, errorArr.length, IPAddress, 9001);
        clientSocket.send(dp);
        System.out.println("Error msg sent.");
        
    }

    /**
     * check if packet is last(less than 516 if yes, return true to terminate
     * transfer if no, continue
     *
     * @param receivePacket
     * @return
     */
    private boolean checkLastPacket(DatagramPacket receivePacket) {
        return receivePacket.getLength() < 516;
    }

    /**
     * send acknowledge message to server
     *
     * @param blockNum
     */
    private void sendACK(byte[] blockNum) throws IOException {
        
        ByteArrayOutputStream ackbaos = new ByteArrayOutputStream(4);
        ackbaos.write(0);
        ackbaos.write(ACK);
        ackbaos.write(blockNum[0]);
        ackbaos.write(blockNum[1]);
        
        byte[] ackArr = ackbaos.toByteArray();
        
        DatagramPacket sendAckMsg = new DatagramPacket(ackArr, ackArr.length, IPAddress, 9001);
        clientSocket.send(sendAckMsg);
    }
    
}
