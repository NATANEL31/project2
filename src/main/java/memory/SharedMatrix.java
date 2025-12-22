package memory;

import java.util.Objects;

public class SharedMatrix {

    private volatile SharedVector[] vectors = {}; // underlying vectors

    public SharedMatrix() {
        // TODO: initialize empty matrix
        this.vectors = new SharedVector[0];
    }

    public SharedMatrix(double[][] matrix) {
        // TODO: construct matrix as row-major SharedVectors
        if (matrix != null) {
            loadRowMajor(matrix);
        }
    }

    public void loadRowMajor(double[][] matrix) {
        // TODO: replace internal data with new row-major matrix
        Objects.requireNonNull(matrix, "Matrix data cannot be null");
        
        SharedVector[] newVectors = new SharedVector[matrix.length];
        for (int i = 0; i < matrix.length; i++) {
            newVectors[i] = new SharedVector(matrix[i], VectorOrientation.ROW_MAJOR);
        }
        this.vectors = newVectors;
    }


    public void loadColumnMajor(double[][] matrix) {
        // TODO: replace internal data with new column-major matrix
        Objects.requireNonNull(matrix, "Matrix data cannot be null");

        int rows = matrix.length;
        int cols = (rows > 0) ? matrix[0].length : 0;

        SharedVector[] newVectors = new SharedVector[cols];

        for (int j = 0; j < cols; j++) {
            double[] colData = new double[rows];
            for (int i = 0; i < rows; i++) {
                colData[i] = matrix[i][j];
            }
            newVectors[j] = new SharedVector(colData, VectorOrientation.COLUMN_MAJOR);
        }
        this.vectors = newVectors;

    }

    public double[][] readRowMajor() {
        // TODO: return matrix contents as a row-major double[][]
        acquireAllVectorReadLocks(this.vectors);
        try {
            int n = vectors.length;
            if (n == 0) return new double[0][0];

            int m = vectors[0].length();
            VectorOrientation orientation = getOrientation();

            double[][] result;

            if (orientation == VectorOrientation.ROW_MAJOR) {
                result = new double[n][m];
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < m; j++) {
                        result[i][j] = vectors[i].get(j); 
                    }
                }
            } 
            else {
                result = new double[m][n];
                for (int col = 0; col < n; col++) {
                    for (int row = 0; row < m; row++) {
                        result[row][col] = vectors[col].get(row);
                    }
                }
            }
            return result;
            } finally {
            
            releaseAllVectorReadLocks(this.vectors);
        }
    }

    public SharedVector get(int index) {
        // TODO: return vector at index
        if (index < 0 || index >= vectors.length) {
            throw new IndexOutOfBoundsException("Index " + index + " is out of bounds");
        }
        return vectors[index];
    }

    public int length() {
        // TODO: return number of stored vectors
        return vectors.length;
    }

    public VectorOrientation getOrientation() {
        // TODO: return orientation
        if (vectors.length == 0) return null;
        return vectors[0].getOrientation();
    }

    private void acquireAllVectorReadLocks(SharedVector[] vecs) {
        // TODO: acquire read lock for each vector
        for (SharedVector v : vecs) {
            v.readLock();
        }
    }

    private void releaseAllVectorReadLocks(SharedVector[] vecs) {
        // TODO: release read locks
        for (SharedVector v : vecs) {
            v.readUnlock();
        }
    }

    private void acquireAllVectorWriteLocks(SharedVector[] vecs) {
        // TODO: acquire write lock for each vector
        for (SharedVector v : vecs) {
            v.writeLock();
        }

    }

    private void releaseAllVectorWriteLocks(SharedVector[] vecs) {
        // TODO: release write locks
        for (SharedVector v : vecs) {
            v.writeUnlock();
        }
    }
}
