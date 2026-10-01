package seneca.client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.event.ActionEvent;
import java.rmi.Naming;
import java.util.Vector;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.BevelBorder;
import javax.swing.border.Border;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.text.html.HTMLEditorKit;
import seneca.core.SenecaDataset;
import seneca.gatekeeper.GateKeeperInterface;
import seneca.gatekeeper.ServerInfo;

public class ComputeServerConfigurator extends JPanel {
   protected JTable table;
   protected JScrollPane scrollpane;
   protected JScrollPane reportScrollPane;
   protected JPanel southPanel;
   protected Box centerPanel;
   protected Box centerCenterPanel;
   Border border = null;
   protected JEditorPane reportPane;
   protected JButton closeButton;
   protected JButton clearListButton;
   protected JButton getServerListButton;
   protected Vector serverList = new Vector();
   SenecaDataset sd;

   public ComputeServerConfigurator(SenecaDataset var1) {
      this.table = new ComputeServerConfigurator$1(this, new ComputeServerConfigurator.ComputeServerConfiguratorTableModel());
      this.sd = var1;
      this.serverList = var1.serverList;
      FontMetrics var2 = this.getFontMetrics(this.table.getFont());

      for (int var3 = 0; var3 < this.table.getColumnCount(); var3++) {
         TableColumn var4 = this.table.getColumn(this.table.getColumnName(var3));
         var4.setWidth((int)(var2.stringWidth(this.table.getColumnName(var3)) * 1.2));
         DefaultTableCellRenderer var5 = new DefaultTableCellRenderer();
         var5.setBackground(Color.yellow);
      }

      Object var10 = null;
      Object var11 = null;
      boolean var6 = false;
      boolean var7 = false;
      new JButton("");
      this.setCellRendering();
      this.scrollpane = new JScrollPane(this.table);
      this.scrollpane.setBorder(new BevelBorder(1));
      this.setLayout(new BorderLayout());
      this.southPanel = new JPanel();
      this.southPanel.setLayout(new FlowLayout());
      this.centerPanel = new Box(1);
      this.centerCenterPanel = new Box(0);
      this.clearListButton = new JButton("Clear List");
      this.clearListButton.addActionListener(new ComputeServerConfigurator.ClearListAction());
      this.getServerListButton = new JButton("Get Serverlist");
      this.getServerListButton.addActionListener(new ComputeServerConfigurator.GetServerListAction());
      this.southPanel.add(this.clearListButton);
      this.southPanel.add(this.getServerListButton);
      this.add("South", this.southPanel);
      this.scrollpane.setPreferredSize(new Dimension(400, 200));
      this.reportPane = new JEditorPane();
      this.reportPane.setEditable(false);
      this.reportPane.setEditorKit(new HTMLEditorKit());
      this.reportPane.setText("No Info available");
      this.reportScrollPane = new JScrollPane(this.reportPane);
      this.reportScrollPane.setPreferredSize(new Dimension(200, 300));
      this.border = BorderFactory.createEtchedBorder();
      String var9 = "Current Server List";
      this.reportScrollPane.setBorder(BorderFactory.createTitledBorder(this.border, var9));
      this.centerPanel.add(this.scrollpane);
      this.centerCenterPanel.add(this.reportScrollPane);
      this.centerPanel.add(this.centerCenterPanel);
      this.add("Center", this.centerPanel);
      this.scrollpane.repaint();
   }

   private String getGateKeeperStatus() {
      String var1 = "";

      try {
         String var2 = "www.nmrshiftdb.org";
         String var3 = "//" + var2 + "/GateKeeper";
         GateKeeperInterface var4 = (GateKeeperInterface)Naming.lookup(var3);
         return "<html>\n" + var4.getFullServerList() + "</html>\n";
      } catch (Exception var5) {
         var5.printStackTrace();
         return "<html><b>Could not establish connection with GateKeeper</b></html>";
      }
   }

   protected void setCellRendering() {
      JLabel var1 = new JLabel();
      this.table.setRowSelectionAllowed(true);
      this.table.setColumnSelectionAllowed(false);

      for (int var3 = 0; var3 < this.table.getColumnCount(); var3++) {
         TableColumn var2 = this.table.getColumn(this.table.getColumnName(var3));
         var1.setVerticalAlignment(0);
         var1.setHorizontalAlignment(0);
         DefaultTableCellRenderer var4 = new DefaultTableCellRenderer();
         var4.setBackground(Color.yellow);
         var2.setCellRenderer(var4);
      }
   }

   class ClearListAction extends AbstractAction {
      ClearListAction() {
         super("ClearListAction");
      }

      public void actionPerformed(ActionEvent var1) {
         ComputeServerConfigurator.this.serverList.setSize(0);
      }
   }

   class ComputeServerConfiguratorTableModel extends AbstractTableModel {
      String[] names = new String[]{"No.", "Running on Host", "Response", "Speed"};

      public int getColumnCount() {
         return this.names.length;
      }

      public int getRowCount() {
         return ComputeServerConfigurator.this.serverList.size();
      }

      public Object getValueAt(int var1, int var2) {
         ServerInfo var3 = (ServerInfo)ComputeServerConfigurator.this.serverList.elementAt(var1);
         switch (var2) {
            case 0:
               return String.valueOf(var1 + 1);
            case 1:
               try {
                  return var3.getHostname();
               } catch (Exception var7) {
                  System.err.println("getHostname(): " + var7.toString());
               }
            case 2:
               try {
                  return new Long(var3.getResponseTime());
               } catch (Exception var6) {
                  System.err.println("getResponseTime(): " + var6.toString());
               }
            case 3:
               try {
                  return new Long(var3.getSpeed());
               } catch (Exception var5) {
                  System.err.println("getSpeed(): " + var5.toString());
               }
            default:
               return "-";
         }
      }

      public String getColumnName(int var1) {
         return this.names[var1];
      }

      public Class getColumnClass(int var1) {
         return this.getValueAt(0, var1).getClass();
      }

      public boolean isCellEditable(int var1, int var2) {
         return var2 != 0;
      }

      public void setValueAt(Object var1, int var2, int var3) {
      }
   }

   class GetServerListAction extends AbstractAction implements Runnable {
      GetServerListAction() {
         super("GetServerListAction");
      }

      public void actionPerformed(ActionEvent var1) {
         new Thread(this).run();
      }

      public void run() {
         ComputeServerConfigurator.this.reportPane.setText(ComputeServerConfigurator.this.getGateKeeperStatus());
         ComputeServerConfigurator.this.reportScrollPane.revalidate();
      }
   }
}
