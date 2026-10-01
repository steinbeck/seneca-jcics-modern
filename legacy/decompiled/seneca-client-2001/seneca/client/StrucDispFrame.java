package seneca.client;

import compchem.Atom;
import compchem.ChemFileUtils;
import compchem.DataStructureTools;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.Vector;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

public class StrucDispFrame extends JFrame {
   protected StrucDispFrame.StrucContainer panel;
   protected JScrollPane scrollPane;
   protected int noOfStructures = 0;
   protected Dimension jmdDim = new Dimension(300, 200);
   protected JPanel southPanel;
   protected JButton closeButton;
   protected JButton saveNativeButton;
   protected JButton loadNativeButton;
   protected JButton saveMDLButton;
   protected JButton loadMDLButton;
   protected JButton loadLUCYButton;
   protected JButton isoButton;
   protected Vector jmdPanels = new Vector();
   public static boolean standAlone = false;

   public StrucDispFrame() {
      this.getContentPane().setLayout(new BorderLayout());
      this.setTitle("Structure Display");
      this.panel = new StrucDispFrame.StrucContainer(this);
      this.scrollPane = new JScrollPane(this.panel);
      this.southPanel = new JPanel();
      this.southPanel.setLayout(new GridLayout(2, 4));
      this.closeButton = new JButton("Close");
      this.closeButton.addActionListener(new StrucDispFrame.closeAction(this));
      this.loadMDLButton = new JButton("Load MDL");
      this.loadMDLButton.addActionListener(new StrucDispFrame.OpenMDLAction(this));
      this.saveMDLButton = new JButton("Save MDL");
      this.saveMDLButton.addActionListener(new StrucDispFrame.SaveMDLAction(this));
      this.loadLUCYButton = new JButton("Load LUCY Result");
      this.loadLUCYButton.addActionListener(new StrucDispFrame.OpenLUCYAction(this));
      this.isoButton = new JButton("Remove Isomorphism");
      this.isoButton.addActionListener(new StrucDispFrame.IsoAction(this));
      this.southPanel.add(this.loadMDLButton);
      this.southPanel.add(this.loadLUCYButton);
      this.southPanel.add(this.saveMDLButton);
      this.southPanel.add(this.closeButton);
      this.scrollPane.setPreferredSize(new Dimension(600, 400));
      this.getContentPane().add("Center", this.scrollPane);
      this.getContentPane().add("South", this.southPanel);
      this.setVisible(true);
      this.pack();
   }

   public void addStructure(JPanel var1) {
      String var2 = "Hit no. " + (this.noOfStructures + 1);
      this.addStructure(var1, var2);
   }

   public void addStructure(JPanel var1, String var2) {
      this.noOfStructures++;
      var1.setPreferredSize(this.jmdDim);
      var1.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), var2));
      this.jmdPanels.addElement(var1);
      this.panel.add(var1);
   }

   public static void main(String[] var0) {
      StrucDispFrame var1 = new StrucDispFrame();
      var1.setVisible(true);
      var1.setDefaultCloseOperation(3);
   }

   class IsoAction extends AbstractAction {
      Frame frame;

      IsoAction(Frame var2) {
         super("iso");
         this.frame = var2;
      }

      public void actionPerformed(ActionEvent var1) {
         Vector var4 = new Vector();

         for (int var5 = 0; var5 < StrucDispFrame.this.jmdPanels.size(); var5++) {
            Atom[] var2 = ((JMDrawPanel)StrucDispFrame.this.jmdPanels.elementAt(var5)).getJMDraw().atomSet;
            if (var2 != null && var2.length > 0) {
               var4.addElement(DataStructureTools.cloneAtomSet(var2));
            }
         }

         Atom[] var11 = (Atom[])var4.elementAt(0);

         for (int var6 = 0; var6 < var11.length; var6++) {
            System.out.println(var11[var6].toString());
         }

         System.out.println(var4.size());
         DataStructureTools.removeIsomorphism(var4);
         System.out.println(var4.size());
         StrucDispFrame.this.panel.removeAll();

         for (int var7 = 0; var7 < var4.size(); var7++) {
            JMDrawPanel var3 = new JMDrawPanel();
            var11 = (Atom[])var4.elementAt(var7);
            var3.getJMDraw().atomSet = var11;

            for (int var8 = 0; var8 < var11.length; var8++) {
               System.out.println(var11[var8].toString());
            }

            try {
               StrucDispFrame.this.addStructure(var3);
            } catch (Exception var10) {
            }
         }
      }
   }

   class OpenLUCYAction extends AbstractAction {
      Frame frame;

      OpenLUCYAction(Frame var2) {
         super("openLUCY");
         this.frame = var2;
      }

      public void actionPerformed(ActionEvent var1) {
         Object var4 = null;
         JFileChooser var5 = new JFileChooser();
         var5.setMultiSelectionEnabled(true);
         int var6 = var5.showOpenDialog(this.frame);
         if (var6 == 0) {
            File[] var7 = var5.getSelectedFiles();
            System.out.println("You selected " + var7.length + " files.");

            for (int var8 = 0; var8 < var7.length; var8++) {
               var4 = var7[var8].getAbsolutePath();
               System.out.println("Loading file " + var4);
               Atom[] var2 = ChemFileUtils.readLUCYStructure((String)var4);
               JMDrawPanel var3 = new JMDrawPanel();
               var3.getJMDraw().atomSet = DataStructureTools.cloneAtomSet(var2);
               var3.getJMDraw().draw();
               StrucDispFrame.this.addStructure(var3, var7[var8].getName());
            }
         }
      }
   }

   class OpenMDLAction extends AbstractAction {
      Frame frame;

      OpenMDLAction(Frame var2) {
         super("openMDL");
         this.frame = var2;
      }

      public void actionPerformed(ActionEvent var1) {
         Object var4 = null;
         JFileChooser var5 = new JFileChooser();
         var5.setMultiSelectionEnabled(true);
         int var6 = var5.showOpenDialog(this.frame);
         if (var6 == 0) {
            File[] var7 = var5.getSelectedFiles();
            System.out.println("You selected " + var7.length + " files.");

            for (int var8 = 0; var8 < var7.length; var8++) {
               var4 = var7[var8].getAbsolutePath();
               System.out.println("Loading file " + var4);
               Atom[] var2 = ChemFileUtils.readMDLMolFile((String)var4);
               JMDrawPanel var3 = new JMDrawPanel();
               var3.getJMDraw().atomSet = DataStructureTools.cloneAtomSet(var2);
               var3.getJMDraw().draw();
               StrucDispFrame.this.addStructure(var3, var7[var8].getName());
            }
         }
      }
   }

   class SaveMDLAction extends AbstractAction {
      Frame frame;

      SaveMDLAction(Frame var2) {
         super("saveMDL");
         this.frame = var2;
      }

      public void actionPerformed(ActionEvent var1) {
         FileDialog var7 = new FileDialog(this.frame, "Save MDL MOL Files", 1);
         var7.show();
         String var4 = var7.getFile();
         String var5 = var7.getDirectory();
         if (var4 == null) {
            System.err.println("Error while trying open file.");
         } else {
            int var8 = var4.lastIndexOf("mol");
            if (var8 == var4.lastIndexOf(".") + 1) {
               var4 = var4.substring(0, var4.lastIndexOf("."));
            }

            for (int var9 = 0; var9 < StrucDispFrame.this.jmdPanels.size(); var9++) {
               Atom[] var2 = ((JMDrawPanel)StrucDispFrame.this.jmdPanels.elementAt(var9)).getJMDraw().atomSet;
               String var6 = var4 + String.valueOf(var9) + ".mol";
               ChemFileUtils.writeMDLMolFile(var5 + var6, var6, var2);
            }
         }
      }
   }

   class StrucContainer extends JPanel {
      JFrame frame;

      public StrucContainer(JFrame var2) {
         this.frame = var2;
         this.setLayout(new FlowLayout(0, 0, 0));
      }

      public Dimension getPreferredSize() {
         int var1 = this.frame.getContentPane().getSize().width;
         if (var1 < StrucDispFrame.this.jmdDim.width) {
            var1 = StrucDispFrame.this.jmdDim.width;
         }

         int var2 = (StrucDispFrame.this.noOfStructures / (var1 / StrucDispFrame.this.jmdDim.width) + 1) * StrucDispFrame.this.jmdDim.height;
         var2 = (int)(var2 * 1.2);
         return new Dimension(var1, var2);
      }

      public Rectangle getBounds() {
         return new Rectangle(new Point(0, 0), this.getPreferredSize());
      }
   }

   class closeAction extends AbstractAction {
      JFrame frame;

      closeAction(JFrame var2) {
         super("CloseTableFrame");
         this.frame = var2;
      }

      public void actionPerformed(ActionEvent var1) {
         this.frame.dispose();
      }
   }
}
