package memory;

import java.util.Objects;

public class SharedMatrix {

    private volatile SharedVector[] vectors = {}; // underlying vectors
    private volatile VectorOrientation orientation; // added field


    public SharedMatrix() {
        // TODO: initialize empty matrix
        this.vectors = new SharedVector[0];
        this.orientation = VectorOrientation.ROW_MAJOR;
    }

    public SharedMatrix(double[][] matrix) {
        // TODO: construct matrix as row-major SharedVectors
        loadRowMajor(matrix);

    }

    public void loadRowMajor(double[][] matrix) {
        // TODO: replace internal data with new row-major matrix
        load(matrix, VectorOrientation.ROW_MAJOR);

    }

    public void loadColumnMajor(double[][] matrix) {
        // TODO: replace internal data with new column-major matrix
        load(matrix, VectorOrientation.COLUMN_MAJOR);

    }

    public double[][] readRowMajor() {
        // TODO: return matrix contents as a row-major double[][]
        
        // Snapshot current references
        SharedVector[] vecs = this.vectors;
        VectorOrientation ori = this.orientation;

        // 0x0 case
        if (vecs.length == 0) {
            return new double[0][0];
        }

        // Lock all vectors for consistent read
        acquireAllVectorReadLocks(vecs);
        try {
            if (ori == VectorOrientation.ROW_MAJOR) {
                int rows = vecs.length;
                int cols = vecs[0].length();
                double[][] out = new double[rows][cols];

                for (int i = 0; i < rows; i++) {
                    if (vecs[i].length() != cols) {
                        throw new IllegalStateException("Corrupt SharedMatrix: inconsistent row lengths");
                    }
                    for (int j = 0; j < cols; j++) {
                        out[i][j] = vecs[i].get(j);
                    }
                }
                return out;
            } else {
                // COLUMN_MAJOR: vecs are columns
                int cols = vecs.length;
                int rows = vecs[0].length();
                double[][] out = new double[rows][cols];

                for (int j = 0; j < cols; j++) {
                    if (vecs[j].length() != rows) {
                        throw new IllegalStateException("Corrupt SharedMatrix: inconsistent column lengths");
                    }
                    for (int i = 0; i < rows; i++) {
                        out[i][j] = vecs[j].get(i);
                    }
                }
                return out;
            }
        } finally {
            releaseAllVectorReadLocks(vecs);
        } 
    }

    public SharedVector get(int index) {
        // TODO: return vector at index
        return this.vectors[index];
    }

    public int length() {
        // TODO: return number of stored vectors
        return this.vectors.length;

    }

    public VectorOrientation getOrientation() {
        // TODO: return orientation
        return this.orientation;
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

    private void load(double[][] matrix, VectorOrientation target) {
        Objects.requireNonNull(matrix, "matrix must not be null");
        Objects.requireNonNull(target, "target must not be null");

        // Validate rectangular shape
        int rows = matrix.length;
        int cols = (rows == 0 ? 0 : requireNonNullRow(matrix, 0).length);

        for (int i = 0; i < rows; i++) {
            double[] row = requireNonNullRow(matrix, i);
            if (row.length != cols) {
                throw new IllegalArgumentException(
                    "Non-rectangular matrix: row " + i +
                    " has length " + row.length + " but expected " + cols
                );
            }
        }

        // Build vectors in requested orientation
        SharedVector[] newVecs;
        if (target == VectorOrientation.ROW_MAJOR) {
            newVecs = new SharedVector[rows];
            for (int i = 0; i < rows; i++) {
                newVecs[i] = new SharedVector(matrix[i].clone(), VectorOrientation.ROW_MAJOR);
            }
        } else { // COLUMN_MAJOR
            newVecs = new SharedVector[cols];
            for (int j = 0; j < cols; j++) {
                double[] col = new double[rows];
                for (int i = 0; i < rows; i++) {
                    col[i] = matrix[i][j];
                }
                newVecs[j] = new SharedVector(col, VectorOrientation.COLUMN_MAJOR);
            }
        }

        // Publish
        this.vectors = newVecs;
        this.orientation = target;
    }

    private static double[] requireNonNullRow(double[][] matrix, int i) {
        double[] row = matrix[i];
        if (row == null) {
            throw new IllegalArgumentException("matrix row " + i + " is null");
        }
        return row;
    }
}
