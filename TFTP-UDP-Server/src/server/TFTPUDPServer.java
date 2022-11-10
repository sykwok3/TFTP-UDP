/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package server;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.io.*;

/**
 *
 * @author 236197
 */
public class TFTPUDPServer extends Thread {

    //op code
    private final byte RRQ = 1;
    private final byte WRQ = 2;
    private final byte DATA = 3;
    private final byte ACK = 4;
    private final byte ERROR = 5;

    byte[] receiveData = new byte[516];
    byte[] sendData = new byte[516];

    private DatagramPacket sendPacket;
    private DatagramPacket receivePacket;

    private DatagramSocket serverSocket = null;
    private InetAddress IPAddress;

    String fileName = "";
    private int port;

    public static void main(String[] args) throws Exception {
        TFTPUDPServer start = new TFTPUDPServer();
        start.receive();
    }

    /**
     * method to process received data
     *
     * @throws java.io.IOException
     */
    private void receive() throws IOException {
        serverSocket = new DatagramSocket(9001);
        while (true) {
            receivePacket = new DatagramPacket(receiveData, receiveData.length);
            serverSocket.receive(receivePacket);
            IPAddress = receivePacket.getAddress();
            port = receivePacket.getPort();

            byte[] sortOpCode = {receiveData[0], receiveData[1]};
            if (sortOpCode[1] == RRQ) {
                System.out.println("RRQ received");
                sendData(receivePacket);

            } else if (sortOpCode[1] == WRQ) {
                System.out.println("WRQ received");
                getFileName(receivePacket);
                byte[] blockNum = null;
                sendACK(blockNum);
            } else if (sortOpCode[1] == DATA) {
                System.out.println("DATA received");
                // sendACK(); ////this part not working, need to get last packet

                String fileText = new String(receivePacket.getData());

                System.out.println("FROM client: " + fileText);
                try {
                    FileOutputStream fos = new FileOutputStream(fileName);
                    fos.write(receivePacket.getData(), 4, receivePacket.getLength() - 4);
                    fos.close();
                } catch (FileNotFoundException e) {
                    System.err.println("File not found.");
                    sendError(e.getMessage());
                }
                if (checkLastPacket(receivePacket) == false){
                    serverSocket.close();
                }


            } else if (sortOpCode[1] == ACK) {
                System.out.println("ACK received");
                //process data
                //check if last
            } else if ((sortOpCode[1] == ERROR)) {
                String errorMSG = new String(receiveData, 4, receiveData.length - 4);
                System.out.println("Error message: " + errorMSG);
            }
        }
    }

    /**
     * method to send error message
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

        DatagramPacket dp = new DatagramPacket(errorArr, errorArr.length, IPAddress, port);
        serverSocket.send(dp);
        System.out.println("Error msg sent.");

    }

    /**
     * method to get file name
     *
     * @param receivePacket
     */
    private void getFileName(DatagramPacket receivePacket) {
        System.out.println("Getting file name.");
        receiveData[1] = 0;
        String text = new String(receivePacket.getData());
        String txt = ".txt";
        String file = "";
        fileName = text.substring(2, text.indexOf(txt) + 4);
        System.out.println("File name: " + fileName);

    }

    /**
     * method to send data(file)
     *
     * @param receivePacket
     * @throws java.io.IOException
     */
    private void sendData(DatagramPacket receivePacket) throws IOException {
        receiveData[1] = 0;
        String text = new String(receivePacket.getData());
        String txt = ".txt";
        String file = "";
        fileName = text.substring(2, text.indexOf(txt) + 4);
        System.out.println("File name to be sent: " + fileName);

        try {
            //convert file to byte and adds the data pack header
            BufferedReader fileInput = new BufferedReader(new FileReader(fileName));
            System.out.println("File found.");
            byte fileData[] = new byte[516];
            String nextLine = fileInput.readLine();
            String store = "";
            while (nextLine != null) {
                store = store + nextLine;
                nextLine = fileInput.readLine();
            }
            fileData = store.getBytes();
            byte[] blockNum = {0, 1};
            byte[] dataMSG = {0, DATA, blockNum[0], blockNum[1]};

            System.arraycopy(dataMSG, 0, sendData, 0, dataMSG.length);
            System.arraycopy(fileData, 0, sendData, dataMSG.length, fileData.length);

            DatagramPacket dp = new DatagramPacket(sendData, sendData.length, IPAddress, port);
            serverSocket.send(dp);
            System.out.println("Data sent.");
            blockNum[1]++;
        } catch (FileNotFoundException e) {
            System.err.println("File not found.");
            sendError(e.getMessage());
        }

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
        
        DatagramPacket sendAckMsg = new DatagramPacket(ackArr, ackArr.length, IPAddress, port);
        serverSocket.send(sendAckMsg);
    }

    /**
     * method to check if packet is last return true if packet is last(length
     * less than 516)
     *
     * @param receivePacket
     * @return
     */
    private boolean checkLastPacket(DatagramPacket receivePacket) {
        return receivePacket.getLength() < 516;
    }

}
