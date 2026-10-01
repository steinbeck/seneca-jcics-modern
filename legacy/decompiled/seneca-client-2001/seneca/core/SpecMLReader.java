package seneca.core;

import compchem.Atom;
import java.io.File;
import java.io.IOException;
import java.util.Vector;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.AttributeList;
import org.xml.sax.HandlerBase;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import seneca.gatekeeper.ServerInfo;
import seneca.structgen.AnnealingSchedule;

public class SpecMLReader extends HandlerBase implements SpecMLConstants {
   Document document = null;
   Vector atoms = new Vector();
   SenecaDataset sd = null;
   static boolean docStart = false;
   static boolean parsingDone = true;
   static Node startNode = null;
   static boolean debug = false;

   public SpecMLReader(File var1) {
      this.document = openDocument(var1);
      this.readSDDomTree();
      parsingDone = true;
   }

   public static Document openDocument(File var0) {
      Document var1 = null;
      Object var2 = null;
      DocumentBuilderFactory var3 = DocumentBuilderFactory.newInstance();

      try {
         DocumentBuilder var4 = var3.newDocumentBuilder();
         var1 = var4.parse(var0);
      } catch (SAXParseException var8) {
         if (debug) {
            System.out.println("\n** Parsing error, line " + var8.getLineNumber() + ", uri " + var8.getSystemId());
         }

         if (debug) {
            System.out.println("   " + var8.getMessage());
         }

         Object var5 = var8;
         if (var8.getException() != null) {
            var5 = var8.getException();
         }

         var5.printStackTrace();
      } catch (SAXException var9) {
         Object var6 = var9;
         if (var9.getException() != null) {
            var6 = var9.getException();
         }

         var6.printStackTrace();
      } catch (ParserConfigurationException var10) {
         var10.printStackTrace();
      } catch (IOException var11) {
         var11.printStackTrace();
      }

      return var1;
   }

   public static Node getDocStart(Node var0) {
      return getDocStart(var0, "senecadataset");
   }

   public static Node getDocStart(Node var0, String var1) {
      Object var2 = null;

      for (int var3 = 0; var3 < var0.getChildNodes().getLength(); var3++) {
         var2 = var0.getChildNodes().item(var3);
         if (debug) {
            System.out.println(var0.getNodeName().toLowerCase());
         }

         if (var0.getNodeName().trim().toLowerCase().equals(var1.trim().toLowerCase())) {
            docStart = true;
            startNode = var0;
            return startNode;
         }
      }

      for (int var4 = 0; var4 < var0.getChildNodes().getLength(); var4++) {
         var2 = var0.getChildNodes().item(var4);
         getDocStart((Node)var2, var1);
         if (docStart) {
            return startNode;
         }
      }

      if (debug) {
         System.out.println("Nothing found");
      }

      return null;
   }

   private void readSDDomTree() {
      Node var1 = getDocStart(this.document);
      if (this.initSD(var1)) {
         this.handleSDParts(var1);
      }
   }

   private boolean initSD(Node var1) {
      this.sd = new SenecaDataset(var1.getAttributes().getNamedItem("title").getNodeValue());
      return true;
   }

   private void handleSDParts(Node var1) {
      Node var2 = null;
      Object var3 = null;

      for (int var4 = 0; var4 < var1.getChildNodes().getLength(); var4++) {
         var2 = var1.getChildNodes().item(var4);
         if (SpecMLConstants.typeName[var2.getNodeType()].equals("Element")) {
            var3 = var2.getNodeName();
            if (var3.toLowerCase().equals("formula")) {
               this.readFormula(var2);
            }

            if (var3.toLowerCase().equals("cml:molecule")) {
               this.readMolecule(var2);
            }

            if (var3.toLowerCase().equals("spectrum")) {
               this.readSpectrum(var2);
            }

            if (var3.toLowerCase().equals("judge")) {
            }

            if (var3.toLowerCase().equals("annealingschedule")) {
               this.readAnnealingSchedule(var2);
            }

            if (var3.toLowerCase().equals("serverinfo")) {
               this.sd.serverList.addElement(readServerInfo(var2));
            }
         }
      }
   }

   private void readJudge(Node var1) {
      if (debug) {
         System.out.println("readJudge");
      }

      Object var2 = null;
      String var3 = var1.getAttributes().getNamedItem("title").getNodeValue();
      String var4 = var3 + "Configurator";
      Class var5 = null;
      Object var6 = null;
      Class[] var7 = new Class[1];
      Object[] var8 = new Object[1];

      try {
         var5 = Class.forName("seneca.client." + var4);
      } catch (Exception var11) {
         System.err.println("No Configurator " + var4 + " found for judge " + var4 + "\n");
         var11.printStackTrace();
      }

      try {
         var7[0] = class$org$w3c$dom$Node == null ? (class$org$w3c$dom$Node = class$("org.w3c.dom.Node")) : class$org$w3c$dom$Node;
         var6 = var5.getMethod("getJudgeFromXML", var7);
         var8[0] = var1;
      } catch (Exception var10) {
         System.err.println("No getJudgeFromXML method found for judgeConfigurator " + var4 + "\n");
         var10.printStackTrace();
      }
   }

   private void readMolecule(Node var1) {
      if (debug) {
         System.out.println("readMolecule");
      }

      Object var2 = null;
      Vector var3 = new Vector();

      for (int var4 = 0; var4 < var1.getChildNodes().getLength(); var4++) {
         var2 = var1.getChildNodes().item(var4);
         if (SpecMLConstants.typeName[var2.getNodeType()].equals("Element") && var2.getNodeName().toLowerCase().equals("atom")) {
            var3.addElement(this.readAtom((Node)var2));
         }
      }

      Atom[] var5 = new Atom[var3.size()];

      for (int var6 = 0; var6 < var3.size(); var6++) {
         var5[var6] = (Atom)var3.elementAt(var6);
      }

      this.sd.setSetOfNodes(var5);
   }

   private Atom readAtom(Node var1) {
      if (debug) {
         System.out.println("readAtom");
      }

      Object var2 = null;
      Atom var3 = new Atom();
      var3.HCount = -1;
      var3.name = var1.getAttributes().getNamedItem("id").getNodeValue();

      for (int var4 = 0; var4 < var1.getChildNodes().getLength(); var4++) {
         var2 = var1.getChildNodes().item(var4);
         if (SpecMLConstants.typeName[var2.getNodeType()].equals("Element")) {
            if (var2.getNodeName().toLowerCase().equals("string")
               && var2.getAttributes().getNamedItem("builtin").getNodeValue().toLowerCase().equals("elementtype")) {
               var3.symbol = var2.getChildNodes().item(0).getNodeValue();
               if (debug) {
                  System.out.println(var3.symbol);
               }
            }

            if (var2.getNodeName().toLowerCase().equals("integer")
               && var2.getAttributes().getNamedItem("builtin").getNodeValue().toLowerCase().equals("hydrogencount")) {
               var3.HCount = Integer.parseInt(var2.getChildNodes().item(0).getNodeValue());
               if (debug) {
                  System.out.println(var3.HCount);
               }
            }

            if (var2.getNodeName().toLowerCase().equals("float")
               && var2.getAttributes().getNamedItem("title").getNodeValue().toLowerCase().equals("assignedcarbonshift")) {
               var3.setPhysicalProperty("13C chemical shift", new Float(var2.getChildNodes().item(0).getNodeValue()));
               if (debug) {
                  System.out.println(var3.getPhysicalProperty("13C chemical shift"));
               }
            }
         }
      }

      return var3;
   }

   private void readSpectrum(Node var1) {
      if (debug) {
         System.out.println("readSpectrum");
      }

      Object var2 = null;
      NMRSpectrum var3 = null;
      var3 = this.readSpectrumInfo(var1);
      var3.name = var1.getAttributes().getNamedItem("id").getNodeValue();

      for (int var4 = 0; var4 < var1.getChildNodes().getLength(); var4++) {
         var2 = var1.getChildNodes().item(var4);
         if (SpecMLConstants.typeName[var2.getNodeType()].equals("Element") && var2.getNodeName().toLowerCase().equals("signal")) {
            var3.addSignal(this.readNMRSignal((Node)var2, var3.nucleus));
         }
      }

      if (debug) {
         System.out.println("We found an nmr spectrum of type " + var3.specType);
      }

      if (var3.specType.equals(SenecaDataset.carbon1d_name)) {
         this.sd.carbon1D = var3;
      }

      if (var3.specType.equals(SenecaDataset.dept90_name)) {
         this.sd.dept90 = var3;
      }

      if (var3.specType.equals(SenecaDataset.dept135_name)) {
         this.sd.dept135 = var3;
      }

      if (var3.specType.equals(SenecaDataset.hhcosy_name)) {
         this.sd.hhcosy = var3;
      }

      if (var3.specType.equals(SenecaDataset.hetcor_name)) {
         this.sd.hetcor = var3;
      }

      if (var3.specType.equals(SenecaDataset.hetcorlr_name)) {
         this.sd.hetcorlr = var3;
      }

      if (var3.specType.equals(SenecaDataset.noesy_name)) {
         this.sd.noesy = var3;
      }
   }

   private NMRSpectrum readSpectrumInfo(Node var1) {
      if (debug) {
         System.out.println("readSpectrumInfo");
      }

      Object var2 = null;
      int var3 = 0;
      String var4 = null;
      String var5 = null;
      Object var6 = null;
      float var7 = 0.0F;
      String[] var8 = new String[4];
      int var9 = 0;
      NMRSpectrum var10 = null;

      for (int var11 = 0; var11 < var1.getChildNodes().getLength(); var11++) {
         var2 = var1.getChildNodes().item(var11);
         if (SpecMLConstants.typeName[var2.getNodeType()].equals("Element") && var2.getNodeName().toLowerCase().equals("spectruminfo")) {
            var1 = (Node)var2;

            for (int var12 = 0; var12 < var1.getChildNodes().getLength(); var12++) {
               var2 = var1.getChildNodes().item(var12);
               if (SpecMLConstants.typeName[var2.getNodeType()].equals("Element")) {
                  if (var2.getNodeName().toLowerCase().equals("dimension") && var2.getChildNodes().item(0) != null) {
                     var3 = Integer.parseInt(var2.getChildNodes().item(0).getNodeValue());
                  }

                  if (var2.getNodeName().toLowerCase().equals("type") && var2.getChildNodes().item(0) != null) {
                     var4 = var2.getChildNodes().item(0).getNodeValue();
                  }

                  if (var2.getNodeName().toLowerCase().equals("frequency") && var2.getChildNodes().item(0) != null) {
                     var7 = Float.parseFloat(var2.getChildNodes().item(0).getNodeValue());
                  }

                  if (var2.getNodeName().toLowerCase().equals("solvent") && var2.getChildNodes().item(0) != null) {
                     var5 = var2.getChildNodes().item(0).getNodeValue();
                  }

                  if (var2.getNodeName().toLowerCase().equals("standard") && var2.getChildNodes().item(0) != null) {
                     var6 = var2.getChildNodes().item(0).getNodeValue();
                  }

                  if (var2.getNodeName().toLowerCase().equals("axisinfo")) {
                     this.readAxisInfo((Node)var2, var8);
                     var9++;
                  }
               }
            }
         }
      }

      String[] var18 = new String[var9];
      System.arraycopy(var8, 0, var18, 0, var18.length);
      var10 = new NMRSpectrum(var18, null);
      var10.setSolvent(var5);
      var10.specType = var4;
      var10.setSpectrometerFrequency(var7);
      return var10;
   }

   private void readAxisInfo(Node var1, String[] var2) {
      if (debug) {
         System.out.println("readAxisInfo");
      }

      String var3 = null;
      Object var4 = null;
      Object var5 = null;
      Object var6 = null;
      boolean var7 = false;
      byte var8 = -1;
      Object var9 = null;
      Object var10 = null;
      Object var11 = null;
      float var12 = 0.0F;
      String var13 = var1.getAttributes().getNamedItem("role").getNodeValue();
      if (var13.toLowerCase().equals("f1")) {
         var8 = 0;
      }

      if (var13.toLowerCase().equals("f2")) {
         var8 = 1;
      }

      if (var13.toLowerCase().equals("f3")) {
         var8 = 2;
      }

      if (var13.toLowerCase().equals("f4")) {
         var8 = 3;
      }

      if (var13.toLowerCase().equals("f5")) {
         var8 = 4;
      }

      for (int var14 = 0; var14 < var1.getChildNodes().getLength(); var14++) {
         var6 = var1.getChildNodes().item(var14);
         if (SpecMLConstants.typeName[var6.getNodeType()].equals("Element")) {
            if (var6.getNodeName().toLowerCase().equals("nucleus")) {
               var3 = var6.getChildNodes().item(0).getNodeValue();
            }

            if (var6.getNodeName().toLowerCase().equals("property")) {
               var4 = var6.getChildNodes().item(0).getNodeValue();
            }

            if (var6.getNodeName().toLowerCase().equals("unit")) {
               var5 = var6.getChildNodes().item(0).getNodeValue();
            }
         }
      }

      var2[var8] = var3;
   }

   private NMRSignal readNMRSignal(Node var1, String[] var2) {
      if (debug) {
         System.out.println("readNMRSignal");
      }

      Object var3 = null;
      boolean var4 = false;
      byte var5 = -1;
      NMRSignal var6 = new NMRSignal(var2);

      for (int var7 = 0; var7 < var1.getChildNodes().getLength(); var7++) {
         var3 = var1.getChildNodes().item(var7);
         if (SpecMLConstants.typeName[var3.getNodeType()].equals("Element")) {
            if (var3.getNodeName().toLowerCase().equals("location")) {
               var5 = -1;
               String var8 = var3.getAttributes().getNamedItem("role").getNodeValue();
               if (var8.toLowerCase().equals("f1")) {
                  var5 = 0;
               }

               if (var8.toLowerCase().equals("f2")) {
                  var5 = 1;
               }

               if (var8.toLowerCase().equals("f3")) {
                  var5 = 2;
               }

               if (var8.toLowerCase().equals("f4")) {
                  var5 = 3;
               }

               if (var8.toLowerCase().equals("f5")) {
                  var5 = 4;
               }

               if (var3.getChildNodes().item(0) != null) {
                  var6.setShift(Float.parseFloat(var3.getChildNodes().item(0).getNodeValue()), var5);
               }
            }

            if (var3.getNodeName().toLowerCase().equals("intensity") && var3.getChildNodes().item(0) != null) {
               var6.intensity = Float.parseFloat(var3.getChildNodes().item(0).getNodeValue());
            }

            if (var3.getNodeName().toLowerCase().equals("normalizefactor") && var3.getChildNodes().item(0) != null) {
               var6.normalizeFactor = Float.parseFloat(var3.getChildNodes().item(0).getNodeValue());
            }

            if (var3.getNodeName().toLowerCase().equals("phase") && var3.getChildNodes().item(0) != null) {
               var6.phase = Integer.parseInt(var3.getChildNodes().item(0).getNodeValue());
            }
         }
      }

      return var6;
   }

   private void readFormula(Node var1) {
      if (debug) {
         System.out.println("readFormula");
      }

      Object var2 = null;

      for (int var3 = 0; var3 < var1.getChildNodes().getLength(); var3++) {
         var2 = var1.getChildNodes().item(var3);
         if (SpecMLConstants.typeName[var2.getNodeType()].equals("Text")) {
            this.sd.setMolecularFormula(var2.getNodeValue());
         }
      }
   }

   private void readAnnealingSchedule(Node var1) {
      if (debug) {
         System.out.println("readAnnealingSchedule");
      }

      Object var2 = null;
      AnnealingSchedule var3 = new AnnealingSchedule();

      for (int var4 = 0; var4 < var1.getChildNodes().getLength(); var4++) {
         var2 = var1.getChildNodes().item(var4);
         if (SpecMLConstants.typeName[var2.getNodeType()].equals("Element")) {
            if (var2.getNodeName().toLowerCase().equals("initialAcceptanceProbability".toLowerCase())) {
               var3.initialAcceptanceProbability = Double.parseDouble(var2.getChildNodes().item(0).getNodeValue());
            }

            if (var2.getNodeName().toLowerCase().equals("maxUphillSteps".toLowerCase())) {
               var3.maxUphillSteps = Long.parseLong(var2.getChildNodes().item(0).getNodeValue());
            }

            if (var2.getNodeName().toLowerCase().equals("plateauSteps".toLowerCase())) {
               var3.plateauSteps = Long.parseLong(var2.getChildNodes().item(0).getNodeValue());
            }

            if (var2.getNodeName().toLowerCase().equals("asymptoticCoolingFactor".toLowerCase())) {
               var3.asymptoticCoolingFactor = Double.parseDouble(var2.getChildNodes().item(0).getNodeValue());
            }
         }
      }

      this.sd.annealingSchedules.addElement(var3);
   }

   public static ServerInfo readServerInfo(Node var0) {
      if (debug) {
         System.out.println("readServerInfo");
      }

      Object var1 = null;
      String var2 = null;
      long var3 = ServerInfo.UNDEFINED;
      long var5 = ServerInfo.UNDEFINED;

      for (int var7 = 0; var7 < var0.getChildNodes().getLength(); var7++) {
         var1 = var0.getChildNodes().item(var7);
         if (SpecMLConstants.typeName[var1.getNodeType()].equals("Element")) {
            if (var1.getNodeName().toLowerCase().equals("hostname".toLowerCase())) {
               var2 = var1.getChildNodes().item(0).getNodeValue();
            }

            if (var1.getNodeName().toLowerCase().equals("speed".toLowerCase())) {
               var5 = Long.parseLong(var1.getChildNodes().item(0).getNodeValue());
            }

            if (var1.getNodeName().toLowerCase().equals("responseTime".toLowerCase())) {
               var3 = Long.parseLong(var1.getChildNodes().item(0).getNodeValue());
            }
         }
      }

      ServerInfo var8 = new ServerInfo(var2, var5);
      var8.setResponseTime(var3);
      return var8;
   }

   public SenecaDataset getSenecaDataset() {
      if (this.sd != null && parsingDone) {
         this.sd.init();
         return this.sd;
      } else {
         return null;
      }
   }

   private void initSD(AttributeList var1) {
      this.sd = new SenecaDataset();
      if (var1 != null) {
         for (int var2 = 0; var2 < var1.getLength(); var2++) {
            if (var1.getName(var2).toLowerCase().equals("title")) {
               this.sd.setName(var1.getValue(var2));
            }
         }
      }
   }

   private void buildAtom(Node var1) {
      new Atom();
   }

   public static int index(Node var0, Node var1) {
      int var2 = var0.getChildNodes().getLength();

      for (int var3 = 0; var3 < var2; var3++) {
         Node var4 = var0.getChildNodes().item(var3);
         if (var1 == var4) {
            return var3;
         }
      }

      return -1;
   }
}
