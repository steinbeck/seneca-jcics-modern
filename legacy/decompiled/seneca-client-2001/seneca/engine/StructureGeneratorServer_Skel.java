package seneca.engine;

import compchem.Molecule;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.rmi.MarshalException;
import java.rmi.Remote;
import java.rmi.UnmarshalException;
import java.rmi.server.Operation;
import java.rmi.server.RemoteCall;
import java.rmi.server.Skeleton;
import java.rmi.server.SkeletonMismatchException;
import java.util.Vector;

public final class StructureGeneratorServer_Skel implements Skeleton {
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

   public void dispatch(Remote var1, RemoteCall var2, int var3, long var4) throws Exception {
      if (var3 < 0) {
         if (var4 == 7160974304001755861L) {
            var3 = 0;
         } else if (var4 == 7368169337274256807L) {
            var3 = 1;
         } else if (var4 == -8858142821358724604L) {
            var3 = 2;
         } else if (var4 == -3973066801809333383L) {
            var3 = 3;
         } else if (var4 == -3593674562149430960L) {
            var3 = 4;
         } else if (var4 == 7794232004984909866L) {
            var3 = 5;
         } else if (var4 == 6884857679691205481L) {
            var3 = 6;
         } else if (var4 == -1763904433104220931L) {
            var3 = 7;
         } else if (var4 == -8081107751519807347L) {
            var3 = 8;
         } else if (var4 == -2299462163976455648L) {
            var3 = 9;
         } else if (var4 == 6532236040461762059L) {
            var3 = 10;
         } else if (var4 == 5090016361178896674L) {
            var3 = 11;
         } else if (var4 == 2127704079190168205L) {
            var3 = 12;
         } else if (var4 == -651194822030917599L) {
            var3 = 13;
         } else if (var4 == -3276111839397711409L) {
            var3 = 14;
         } else {
            if (var4 != 2884788313730275038L) {
               throw new UnmarshalException("invalid method hash");
            }

            var3 = 15;
         }
      } else if (var4 != -2587967488450059152L) {
         throw new SkeletonMismatchException("interface hash mismatch");
      }

      StructureGeneratorServer var6 = (StructureGeneratorServer)var1;
      switch (var3) {
         case 0:
            var2.releaseInputStream();
            var6.endTask();

            try {
               var2.getResultStream(true);
               break;
            } catch (IOException var196) {
               throw new MarshalException("error marshalling return", var196);
            }
         case 1:
            var2.releaseInputStream();
            var6.executeTask();

            try {
               var2.getResultStream(true);
               break;
            } catch (IOException var195) {
               throw new MarshalException("error marshalling return", var195);
            }
         case 2:
            var2.releaseInputStream();
            String var208 = var6.getHostName();

            try {
               ObjectOutput var214 = var2.getResultStream(true);
               var214.writeObject(var208);
               break;
            } catch (IOException var194) {
               throw new MarshalException("error marshalling return", var194);
            }
         case 3:
            var2.releaseInputStream();
            String var207 = var6.getID();

            try {
               ObjectOutput var213 = var2.getResultStream(true);
               var213.writeObject(var207);
               break;
            } catch (IOException var193) {
               throw new MarshalException("error marshalling return", var193);
            }
         case 4:
            var2.releaseInputStream();
            long var206 = var6.getSpeed();

            try {
               ObjectOutput var9 = var2.getResultStream(true);
               var9.writeLong(var206);
               break;
            } catch (IOException var192) {
               throw new MarshalException("error marshalling return", var192);
            }
         case 5:
            var2.releaseInputStream();
            Object var205 = var6.getTaskResult();

            try {
               ObjectOutput var212 = var2.getResultStream(true);
               var212.writeObject(var205);
               break;
            } catch (IOException var191) {
               throw new MarshalException("error marshalling return", var191);
            }
         case 6:
            var2.releaseInputStream();
            Object var204 = var6.getTaskStatus();

            try {
               ObjectOutput var211 = var2.getResultStream(true);
               var211.writeObject(var204);
               break;
            } catch (IOException var190) {
               throw new MarshalException("error marshalling return", var190);
            }
         case 7:
            var2.releaseInputStream();
            String var203 = var6.getTaskVersion();

            try {
               ObjectOutput var210 = var2.getResultStream(true);
               var210.writeObject(var203);
               break;
            } catch (IOException var189) {
               throw new MarshalException("error marshalling return", var189);
            }
         case 8:
            var2.releaseInputStream();
            String var202 = var6.getVersion();

            try {
               ObjectOutput var209 = var2.getResultStream(true);
               var209.writeObject(var202);
               break;
            } catch (IOException var188) {
               throw new MarshalException("error marshalling return", var188);
            }
         case 9:
            var2.releaseInputStream();
            var6.resetTask();

            try {
               var2.getResultStream(true);
               break;
            } catch (IOException var187) {
               throw new MarshalException("error marshalling return", var187);
            }
         case 10:
            String var201;
            try {
               ObjectInput var218 = var2.getInputStream();
               var201 = (String)var218.readObject();
            } catch (IOException var184) {
               throw new UnmarshalException("error unmarshalling arguments", var184);
            } catch (ClassNotFoundException var185) {
               throw new UnmarshalException("error unmarshalling arguments", var185);
            } finally {
               var2.releaseInputStream();
            }

            var6.setDatasetName(var201);

            try {
               var2.getResultStream(true);
               break;
            } catch (IOException var183) {
               throw new MarshalException("error marshalling return", var183);
            }
         case 11:
            String var200;
            try {
               ObjectInput var217 = var2.getInputStream();
               var200 = (String)var217.readObject();
            } catch (IOException var180) {
               throw new UnmarshalException("error unmarshalling arguments", var180);
            } catch (ClassNotFoundException var181) {
               throw new UnmarshalException("error unmarshalling arguments", var181);
            } finally {
               var2.releaseInputStream();
            }

            var6.setID(var200);

            try {
               var2.getResultStream(true);
               break;
            } catch (IOException var179) {
               throw new MarshalException("error marshalling return", var179);
            }
         case 12:
            Vector var199;
            try {
               ObjectInput var216 = var2.getInputStream();
               var199 = (Vector)var216.readObject();
            } catch (IOException var176) {
               throw new UnmarshalException("error unmarshalling arguments", var176);
            } catch (ClassNotFoundException var177) {
               throw new UnmarshalException("error unmarshalling arguments", var177);
            } finally {
               var2.releaseInputStream();
            }

            var6.setJudges(var199);

            try {
               var2.getResultStream(true);
               break;
            } catch (IOException var175) {
               throw new MarshalException("error marshalling return", var175);
            }
         case 13:
            Molecule var198;
            try {
               ObjectInput var215 = var2.getInputStream();
               var198 = (Molecule)var215.readObject();
            } catch (IOException var172) {
               throw new UnmarshalException("error unmarshalling arguments", var172);
            } catch (ClassNotFoundException var173) {
               throw new UnmarshalException("error unmarshalling arguments", var173);
            } finally {
               var2.releaseInputStream();
            }

            var6.setMolecule(var198);

            try {
               var2.getResultStream(true);
               break;
            } catch (IOException var171) {
               throw new MarshalException("error marshalling return", var171);
            }
         case 14:
            Vector var197;
            try {
               ObjectInput var10 = var2.getInputStream();
               var197 = (Vector)var10.readObject();
            } catch (IOException var168) {
               throw new UnmarshalException("error unmarshalling arguments", var168);
            } catch (ClassNotFoundException var169) {
               throw new UnmarshalException("error unmarshalling arguments", var169);
            } finally {
               var2.releaseInputStream();
            }

            var6.setSASchedules(var197);

            try {
               var2.getResultStream(true);
               break;
            } catch (IOException var167) {
               throw new MarshalException("error marshalling return", var167);
            }
         case 15:
            var2.releaseInputStream();
            boolean var7 = var6.willingToManage();

            try {
               ObjectOutput var8 = var2.getResultStream(true);
               var8.writeBoolean(var7);
               break;
            } catch (IOException var166) {
               throw new MarshalException("error marshalling return", var166);
            }
         default:
            throw new UnmarshalException("invalid method number");
      }
   }

   public Operation[] getOperations() {
      return (Operation[])operations.clone();
   }
}
