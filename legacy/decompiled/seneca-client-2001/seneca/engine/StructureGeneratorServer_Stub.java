package seneca.engine;

import compchem.Molecule;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Method;
import java.rmi.MarshalException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.UnexpectedException;
import java.rmi.UnmarshalException;
import java.rmi.server.Operation;
import java.rmi.server.RemoteCall;
import java.rmi.server.RemoteRef;
import java.rmi.server.RemoteStub;
import java.util.Vector;
import seneca.compute.Compute;

public final class StructureGeneratorServer_Stub extends RemoteStub implements Compute, Remote {
   private static final Operation[] operations = new Operation[]{
      new Operation("void endTask()"),
      new Operation("void executeTask()"),
      new Operation("java.lang.String getHostName()"),
      new Operation("java.lang.String getID()"),
      new Operation("long getSpeed()"),
      new Operation("java.lang.Object getTaskResult()"),
      new Operation("java.lang.Object getTaskStatus()"),
      new Operation("java.lang.String getTaskVersion()"),
      new Operation("java.lang.String getVersion()"),
      new Operation("void resetTask()"),
      new Operation("void setDatasetName(java.lang.String)"),
      new Operation("void setID(java.lang.String)"),
      new Operation("void setJudges(java.util.Vector)"),
      new Operation("void setMolecule(compchem.Molecule)"),
      new Operation("void setSASchedules(java.util.Vector)"),
      new Operation("boolean willingToManage()")
   };
   private static final long interfaceHash = -2587967488450059152L;
   private static final long serialVersionUID = 2L;
   private static boolean useNewInvoke;
   private static Method $method_endTask_0;
   private static Method $method_executeTask_1;
   private static Method $method_getHostName_2;
   private static Method $method_getID_3;
   private static Method $method_getSpeed_4;
   private static Method $method_getTaskResult_5;
   private static Method $method_getTaskStatus_6;
   private static Method $method_getTaskVersion_7;
   private static Method $method_getVersion_8;
   private static Method $method_resetTask_9;
   private static Method $method_setDatasetName_10;
   private static Method $method_setID_11;
   private static Method $method_setJudges_12;
   private static Method $method_setMolecule_13;
   private static Method $method_setSASchedules_14;
   private static Method $method_willingToManage_15;

   static {
      try {
         (class$java$rmi$server$RemoteRef != null ? class$java$rmi$server$RemoteRef : (class$java$rmi$server$RemoteRef = class$("java.rmi.server.RemoteRef")))
            .getMethod(
               "invoke",
               class$java$rmi$Remote != null ? class$java$rmi$Remote : (class$java$rmi$Remote = class$("java.rmi.Remote")),
               class$java$lang$reflect$Method != null ? class$java$lang$reflect$Method : (class$java$lang$reflect$Method = class$("java.lang.reflect.Method")),
               array$Ljava$lang$Object != null ? array$Ljava$lang$Object : (array$Ljava$lang$Object = class$("[Ljava.lang.Object;")),
               long.class
            );
         useNewInvoke = true;
         $method_endTask_0 = (class$seneca$compute$Compute != null
               ? class$seneca$compute$Compute
               : (class$seneca$compute$Compute = class$("seneca.compute.Compute")))
            .getMethod("endTask");
         $method_executeTask_1 = (class$seneca$compute$Compute != null
               ? class$seneca$compute$Compute
               : (class$seneca$compute$Compute = class$("seneca.compute.Compute")))
            .getMethod("executeTask");
         $method_getHostName_2 = (class$seneca$compute$Compute != null
               ? class$seneca$compute$Compute
               : (class$seneca$compute$Compute = class$("seneca.compute.Compute")))
            .getMethod("getHostName");
         $method_getID_3 = (class$seneca$compute$Compute != null
               ? class$seneca$compute$Compute
               : (class$seneca$compute$Compute = class$("seneca.compute.Compute")))
            .getMethod("getID");
         $method_getSpeed_4 = (class$seneca$compute$Compute != null
               ? class$seneca$compute$Compute
               : (class$seneca$compute$Compute = class$("seneca.compute.Compute")))
            .getMethod("getSpeed");
         $method_getTaskResult_5 = (class$seneca$compute$Compute != null
               ? class$seneca$compute$Compute
               : (class$seneca$compute$Compute = class$("seneca.compute.Compute")))
            .getMethod("getTaskResult");
         $method_getTaskStatus_6 = (class$seneca$compute$Compute != null
               ? class$seneca$compute$Compute
               : (class$seneca$compute$Compute = class$("seneca.compute.Compute")))
            .getMethod("getTaskStatus");
         $method_getTaskVersion_7 = (class$seneca$compute$Compute != null
               ? class$seneca$compute$Compute
               : (class$seneca$compute$Compute = class$("seneca.compute.Compute")))
            .getMethod("getTaskVersion");
         $method_getVersion_8 = (class$seneca$compute$Compute != null
               ? class$seneca$compute$Compute
               : (class$seneca$compute$Compute = class$("seneca.compute.Compute")))
            .getMethod("getVersion");
         $method_resetTask_9 = (class$seneca$compute$Compute != null
               ? class$seneca$compute$Compute
               : (class$seneca$compute$Compute = class$("seneca.compute.Compute")))
            .getMethod("resetTask");
         $method_setDatasetName_10 = (class$seneca$compute$Compute != null
               ? class$seneca$compute$Compute
               : (class$seneca$compute$Compute = class$("seneca.compute.Compute")))
            .getMethod("setDatasetName", class$java$lang$String != null ? class$java$lang$String : (class$java$lang$String = class$("java.lang.String")));
         $method_setID_11 = (class$seneca$compute$Compute != null
               ? class$seneca$compute$Compute
               : (class$seneca$compute$Compute = class$("seneca.compute.Compute")))
            .getMethod("setID", class$java$lang$String != null ? class$java$lang$String : (class$java$lang$String = class$("java.lang.String")));
         $method_setJudges_12 = (class$seneca$compute$Compute != null
               ? class$seneca$compute$Compute
               : (class$seneca$compute$Compute = class$("seneca.compute.Compute")))
            .getMethod("setJudges", class$java$util$Vector != null ? class$java$util$Vector : (class$java$util$Vector = class$("java.util.Vector")));
         $method_setMolecule_13 = (class$seneca$compute$Compute != null
               ? class$seneca$compute$Compute
               : (class$seneca$compute$Compute = class$("seneca.compute.Compute")))
            .getMethod("setMolecule", class$compchem$Molecule != null ? class$compchem$Molecule : (class$compchem$Molecule = class$("compchem.Molecule")));
         $method_setSASchedules_14 = (class$seneca$compute$Compute != null
               ? class$seneca$compute$Compute
               : (class$seneca$compute$Compute = class$("seneca.compute.Compute")))
            .getMethod("setSASchedules", class$java$util$Vector != null ? class$java$util$Vector : (class$java$util$Vector = class$("java.util.Vector")));
         $method_willingToManage_15 = (class$seneca$compute$Compute != null
               ? class$seneca$compute$Compute
               : (class$seneca$compute$Compute = class$("seneca.compute.Compute")))
            .getMethod("willingToManage");
      } catch (NoSuchMethodException var0) {
         useNewInvoke = false;
      }
   }

   public StructureGeneratorServer_Stub() {
   }

   public StructureGeneratorServer_Stub(RemoteRef var1) {
      super(var1);
   }

   public void endTask() throws RemoteException {
      try {
         if (useNewInvoke) {
            super.ref.invoke(this, $method_endTask_0, null, 7160974304001755861L);
         } else {
            RemoteCall var1 = super.ref.newCall(this, operations, 0, -2587967488450059152L);
            super.ref.invoke(var1);
            super.ref.done(var1);
         }
      } catch (RuntimeException var2) {
         throw var2;
      } catch (RemoteException var3) {
         throw var3;
      } catch (Exception var4) {
         throw new UnexpectedException("undeclared checked exception", var4);
      }
   }

   public void executeTask() throws RemoteException {
      try {
         if (useNewInvoke) {
            super.ref.invoke(this, $method_executeTask_1, null, 7368169337274256807L);
         } else {
            RemoteCall var1 = super.ref.newCall(this, operations, 1, -2587967488450059152L);
            super.ref.invoke(var1);
            super.ref.done(var1);
         }
      } catch (RuntimeException var2) {
         throw var2;
      } catch (RemoteException var3) {
         throw var3;
      } catch (Exception var4) {
         throw new UnexpectedException("undeclared checked exception", var4);
      }
   }

   public String getHostName() throws RemoteException {
      try {
         if (useNewInvoke) {
            Object var18 = super.ref.invoke(this, $method_getHostName_2, null, -8858142821358724604L);
            return (String)var18;
         } else {
            RemoteCall var1 = super.ref.newCall(this, operations, 2, -2587967488450059152L);
            super.ref.invoke(var1);

            String var2;
            try {
               ObjectInput var5 = var1.getInputStream();
               var2 = (String)var5.readObject();
            } catch (IOException var12) {
               throw new UnmarshalException("error unmarshalling return", var12);
            } catch (ClassNotFoundException var13) {
               throw new UnmarshalException("error unmarshalling return", var13);
            } finally {
               super.ref.done(var1);
            }

            return var2;
         }
      } catch (RuntimeException var15) {
         throw var15;
      } catch (RemoteException var16) {
         throw var16;
      } catch (Exception var17) {
         throw new UnexpectedException("undeclared checked exception", var17);
      }
   }

   public String getID() throws RemoteException {
      try {
         if (useNewInvoke) {
            Object var18 = super.ref.invoke(this, $method_getID_3, null, -3973066801809333383L);
            return (String)var18;
         } else {
            RemoteCall var1 = super.ref.newCall(this, operations, 3, -2587967488450059152L);
            super.ref.invoke(var1);

            String var2;
            try {
               ObjectInput var5 = var1.getInputStream();
               var2 = (String)var5.readObject();
            } catch (IOException var12) {
               throw new UnmarshalException("error unmarshalling return", var12);
            } catch (ClassNotFoundException var13) {
               throw new UnmarshalException("error unmarshalling return", var13);
            } finally {
               super.ref.done(var1);
            }

            return var2;
         }
      } catch (RuntimeException var15) {
         throw var15;
      } catch (RemoteException var16) {
         throw var16;
      } catch (Exception var17) {
         throw new UnexpectedException("undeclared checked exception", var17);
      }
   }

   public long getSpeed() throws RemoteException {
      try {
         if (useNewInvoke) {
            Object var17 = super.ref.invoke(this, $method_getSpeed_4, null, -3593674562149430960L);
            return (Long)var17;
         } else {
            RemoteCall var1 = super.ref.newCall(this, operations, 4, -2587967488450059152L);
            super.ref.invoke(var1);

            long var2;
            try {
               ObjectInput var6 = var1.getInputStream();
               var2 = var6.readLong();
            } catch (IOException var12) {
               throw new UnmarshalException("error unmarshalling return", var12);
            } finally {
               super.ref.done(var1);
            }

            return var2;
         }
      } catch (RuntimeException var14) {
         throw var14;
      } catch (RemoteException var15) {
         throw var15;
      } catch (Exception var16) {
         throw new UnexpectedException("undeclared checked exception", var16);
      }
   }

   public Object getTaskResult() throws RemoteException {
      try {
         if (useNewInvoke) {
            return super.ref.invoke(this, $method_getTaskResult_5, null, 7794232004984909866L);
         } else {
            RemoteCall var1 = super.ref.newCall(this, operations, 5, -2587967488450059152L);
            super.ref.invoke(var1);

            Object var2;
            try {
               ObjectInput var5 = var1.getInputStream();
               var2 = var5.readObject();
            } catch (IOException var12) {
               throw new UnmarshalException("error unmarshalling return", var12);
            } catch (ClassNotFoundException var13) {
               throw new UnmarshalException("error unmarshalling return", var13);
            } finally {
               super.ref.done(var1);
            }

            return var2;
         }
      } catch (RuntimeException var15) {
         throw var15;
      } catch (RemoteException var16) {
         throw var16;
      } catch (Exception var17) {
         throw new UnexpectedException("undeclared checked exception", var17);
      }
   }

   public Object getTaskStatus() throws RemoteException {
      try {
         if (useNewInvoke) {
            return super.ref.invoke(this, $method_getTaskStatus_6, null, 6884857679691205481L);
         } else {
            RemoteCall var1 = super.ref.newCall(this, operations, 6, -2587967488450059152L);
            super.ref.invoke(var1);

            Object var2;
            try {
               ObjectInput var5 = var1.getInputStream();
               var2 = var5.readObject();
            } catch (IOException var12) {
               throw new UnmarshalException("error unmarshalling return", var12);
            } catch (ClassNotFoundException var13) {
               throw new UnmarshalException("error unmarshalling return", var13);
            } finally {
               super.ref.done(var1);
            }

            return var2;
         }
      } catch (RuntimeException var15) {
         throw var15;
      } catch (RemoteException var16) {
         throw var16;
      } catch (Exception var17) {
         throw new UnexpectedException("undeclared checked exception", var17);
      }
   }

   public String getTaskVersion() throws RemoteException {
      try {
         if (useNewInvoke) {
            Object var18 = super.ref.invoke(this, $method_getTaskVersion_7, null, -1763904433104220931L);
            return (String)var18;
         } else {
            RemoteCall var1 = super.ref.newCall(this, operations, 7, -2587967488450059152L);
            super.ref.invoke(var1);

            String var2;
            try {
               ObjectInput var5 = var1.getInputStream();
               var2 = (String)var5.readObject();
            } catch (IOException var12) {
               throw new UnmarshalException("error unmarshalling return", var12);
            } catch (ClassNotFoundException var13) {
               throw new UnmarshalException("error unmarshalling return", var13);
            } finally {
               super.ref.done(var1);
            }

            return var2;
         }
      } catch (RuntimeException var15) {
         throw var15;
      } catch (RemoteException var16) {
         throw var16;
      } catch (Exception var17) {
         throw new UnexpectedException("undeclared checked exception", var17);
      }
   }

   public String getVersion() throws RemoteException {
      try {
         if (useNewInvoke) {
            Object var18 = super.ref.invoke(this, $method_getVersion_8, null, -8081107751519807347L);
            return (String)var18;
         } else {
            RemoteCall var1 = super.ref.newCall(this, operations, 8, -2587967488450059152L);
            super.ref.invoke(var1);

            String var2;
            try {
               ObjectInput var5 = var1.getInputStream();
               var2 = (String)var5.readObject();
            } catch (IOException var12) {
               throw new UnmarshalException("error unmarshalling return", var12);
            } catch (ClassNotFoundException var13) {
               throw new UnmarshalException("error unmarshalling return", var13);
            } finally {
               super.ref.done(var1);
            }

            return var2;
         }
      } catch (RuntimeException var15) {
         throw var15;
      } catch (RemoteException var16) {
         throw var16;
      } catch (Exception var17) {
         throw new UnexpectedException("undeclared checked exception", var17);
      }
   }

   public void resetTask() throws RemoteException {
      try {
         if (useNewInvoke) {
            super.ref.invoke(this, $method_resetTask_9, null, -2299462163976455648L);
         } else {
            RemoteCall var1 = super.ref.newCall(this, operations, 9, -2587967488450059152L);
            super.ref.invoke(var1);
            super.ref.done(var1);
         }
      } catch (RuntimeException var2) {
         throw var2;
      } catch (RemoteException var3) {
         throw var3;
      } catch (Exception var4) {
         throw new UnexpectedException("undeclared checked exception", var4);
      }
   }

   public void setDatasetName(String var1) throws RemoteException {
      try {
         if (useNewInvoke) {
            super.ref.invoke(this, $method_setDatasetName_10, new Object[]{var1}, 6532236040461762059L);
         } else {
            RemoteCall var2 = super.ref.newCall(this, operations, 10, -2587967488450059152L);

            try {
               ObjectOutput var3 = var2.getOutputStream();
               var3.writeObject(var1);
            } catch (IOException var4) {
               throw new MarshalException("error marshalling arguments", var4);
            }

            super.ref.invoke(var2);
            super.ref.done(var2);
         }
      } catch (RuntimeException var5) {
         throw var5;
      } catch (RemoteException var6) {
         throw var6;
      } catch (Exception var7) {
         throw new UnexpectedException("undeclared checked exception", var7);
      }
   }

   public void setID(String var1) throws RemoteException {
      try {
         if (useNewInvoke) {
            super.ref.invoke(this, $method_setID_11, new Object[]{var1}, 5090016361178896674L);
         } else {
            RemoteCall var2 = super.ref.newCall(this, operations, 11, -2587967488450059152L);

            try {
               ObjectOutput var3 = var2.getOutputStream();
               var3.writeObject(var1);
            } catch (IOException var4) {
               throw new MarshalException("error marshalling arguments", var4);
            }

            super.ref.invoke(var2);
            super.ref.done(var2);
         }
      } catch (RuntimeException var5) {
         throw var5;
      } catch (RemoteException var6) {
         throw var6;
      } catch (Exception var7) {
         throw new UnexpectedException("undeclared checked exception", var7);
      }
   }

   public void setJudges(Vector var1) throws RemoteException {
      try {
         if (useNewInvoke) {
            super.ref.invoke(this, $method_setJudges_12, new Object[]{var1}, 2127704079190168205L);
         } else {
            RemoteCall var2 = super.ref.newCall(this, operations, 12, -2587967488450059152L);

            try {
               ObjectOutput var3 = var2.getOutputStream();
               var3.writeObject(var1);
            } catch (IOException var4) {
               throw new MarshalException("error marshalling arguments", var4);
            }

            super.ref.invoke(var2);
            super.ref.done(var2);
         }
      } catch (RuntimeException var5) {
         throw var5;
      } catch (RemoteException var6) {
         throw var6;
      } catch (Exception var7) {
         throw new UnexpectedException("undeclared checked exception", var7);
      }
   }

   public void setMolecule(Molecule var1) throws RemoteException {
      try {
         if (useNewInvoke) {
            super.ref.invoke(this, $method_setMolecule_13, new Object[]{var1}, -651194822030917599L);
         } else {
            RemoteCall var2 = super.ref.newCall(this, operations, 13, -2587967488450059152L);

            try {
               ObjectOutput var3 = var2.getOutputStream();
               var3.writeObject(var1);
            } catch (IOException var4) {
               throw new MarshalException("error marshalling arguments", var4);
            }

            super.ref.invoke(var2);
            super.ref.done(var2);
         }
      } catch (RuntimeException var5) {
         throw var5;
      } catch (RemoteException var6) {
         throw var6;
      } catch (Exception var7) {
         throw new UnexpectedException("undeclared checked exception", var7);
      }
   }

   public void setSASchedules(Vector var1) throws RemoteException {
      try {
         if (useNewInvoke) {
            super.ref.invoke(this, $method_setSASchedules_14, new Object[]{var1}, -3276111839397711409L);
         } else {
            RemoteCall var2 = super.ref.newCall(this, operations, 14, -2587967488450059152L);

            try {
               ObjectOutput var3 = var2.getOutputStream();
               var3.writeObject(var1);
            } catch (IOException var4) {
               throw new MarshalException("error marshalling arguments", var4);
            }

            super.ref.invoke(var2);
            super.ref.done(var2);
         }
      } catch (RuntimeException var5) {
         throw var5;
      } catch (RemoteException var6) {
         throw var6;
      } catch (Exception var7) {
         throw new UnexpectedException("undeclared checked exception", var7);
      }
   }

   public boolean willingToManage() throws RemoteException {
      try {
         if (useNewInvoke) {
            Object var16 = super.ref.invoke(this, $method_willingToManage_15, null, 2884788313730275038L);
            return (Boolean)var16;
         } else {
            RemoteCall var1 = super.ref.newCall(this, operations, 15, -2587967488450059152L);
            super.ref.invoke(var1);

            boolean var2;
            try {
               ObjectInput var5 = var1.getInputStream();
               var2 = var5.readBoolean();
            } catch (IOException var11) {
               throw new UnmarshalException("error unmarshalling return", var11);
            } finally {
               super.ref.done(var1);
            }

            return var2;
         }
      } catch (RuntimeException var13) {
         throw var13;
      } catch (RemoteException var14) {
         throw var14;
      } catch (Exception var15) {
         throw new UnexpectedException("undeclared checked exception", var15);
      }
   }
}
