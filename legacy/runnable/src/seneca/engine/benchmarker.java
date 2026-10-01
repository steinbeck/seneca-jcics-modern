package seneca.engine;

import jnt.scimark2.*;

public class benchmarker implements java.io.Serializable
{

  /* Benchmark 5 kernels with individual Mflops.
	 "results[0]" has the average Mflop rate.

  */


	public static long getBenchmark()
	{
		double min_time = Constants.RESOLUTION_DEFAULT;

		int FFT_size = Constants.FFT_SIZE;
		int SOR_size =  Constants.SOR_SIZE;
		int Sparse_size_M = Constants.SPARSE_SIZE_M;
		int Sparse_size_nz = Constants.SPARSE_SIZE_nz;
		int LU_size = Constants.LU_SIZE;

		// run the benchmark

		double res[] = new double[6];
		Random R = new Random(Constants.RANDOM_SEED);

		res[1] = kernel.measureFFT( FFT_size, min_time, R);
		res[2] = kernel.measureSOR( SOR_size, min_time, R);
		res[3] = kernel.measureMonteCarlo(min_time, R);
		res[4] = kernel.measureSparseMatmult( Sparse_size_M, 
					Sparse_size_nz, min_time, R);
		res[5] = kernel.measureLU( LU_size, min_time, R);


		res[0] = (res[1] + res[2] + res[3] + res[4] + res[5]) / 5;

		return (long) res[0];

	}
  
}
