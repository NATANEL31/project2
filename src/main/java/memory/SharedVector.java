package memory;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.Objects;


public class SharedVector {

    private double[] vector;
    private VectorOrientation orientation;
    private ReadWriteLock lock = new java.util.concurrent.locks.ReentrantReadWriteLock();

    public SharedVector(double[] vector, VectorOrientation orientation) {
        // TODO: store vector data and its orientation
        Objects.requireNonNull(vector, "vector must not be null");
        Objects.requireNonNull(orientation, "orientation must not be null");
        this.vector = vector;
        this.orientation = orientation;
    }

    public double get(int index) {
        // TODO: return element at index (read-locked)
        readLock();
        try {
            return vector[index];
        } finally {
            readUnlock();
        }
    }

    public int length() {
        // TODO: return vector length
        // No need to lock because vector length is final
        return vector.length;
    }

    public VectorOrientation getOrientation() {
        // TODO: return vector orientation

        // Orientation can change becuase of transpose() therefore read-lock is necessary
        readLock();
        try {
            return orientation;
        } finally {
            readUnlock();
        }    
    }

    public void writeLock() {
        // TODO: acquire write lock
        lock.writeLock().lock();

    }

    public void writeUnlock() {
        // TODO: release write lock
        lock.writeLock().unlock();

    }

    public void readLock() {
        // TODO: acquire read lock
        lock.readLock().lock();

    }

    public void readUnlock() {
        // TODO: release read lock
        lock.readLock().unlock();

    }

    public void transpose() {
        // TODO: transpose vector
        writeLock();
        try {
            orientation = (orientation == VectorOrientation.ROW_MAJOR)
                    ? VectorOrientation.COLUMN_MAJOR
                    : VectorOrientation.ROW_MAJOR;
        } finally {
            writeUnlock();
        }
    }

    public void add(SharedVector other) {
        // TODO: add two vectors
        Objects.requireNonNull(other, "other vector must not be null");

        // Dimension check
        if (this.length() != other.length()) {
            throw new IllegalArgumentException("Vector length mismatch");
        }

        // Orientation check
        if (this.getOrientation() != other.getOrientation()) {
            throw new IllegalArgumentException("Vector orientation mismatch");
        }

        // Lock ordering: write(this) -> read(other)
        this.writeLock();
        other.readLock();
        try {
            for (int i = 0; i < vector.length; i++) {
                vector[i] += other.vector[i];
            }
        } finally {
            other.readUnlock();
            this.writeUnlock();
        }
    }

    public void negate() {
        // TODO: negate vector
        writeLock();
        try {
            for (int i = 0; i < vector.length; i++) {
                vector[i] = -vector[i];
            }
        } finally {
            writeUnlock();
        }
    }

    public double dot(SharedVector other) {
        // TODO: compute dot product (row · column)
        Objects.requireNonNull(other, "other vector must not be null");

        // Check orientation
        if (this.getOrientation() != VectorOrientation.ROW_MAJOR ||
            other.getOrientation() != VectorOrientation.COLUMN_MAJOR) {
            throw new IllegalArgumentException("Dot product requires ROW_MAJOR · COLUMN_MAJOR");
        }

        if (this.length() != other.length()) {
            throw new IllegalArgumentException("Dot product length mismatch");
        }

        this.readLock();
        other.readLock();
        try {
            double sum = 0.0;
            for (int i = 0; i < vector.length; i++) {
                sum += this.vector[i] * other.vector[i];
            }
            return sum;
        } finally {
            other.readUnlock();
            this.readUnlock();
        }
    }

    public void vecMatMul(SharedMatrix matrix) {
        // TODO: compute row-vector × matrix
        Objects.requireNonNull(matrix, "matrix must not be null");

        if (this.getOrientation() != VectorOrientation.ROW_MAJOR) {
            throw new IllegalArgumentException("vecMatMul requires this vector to be ROW_MAJOR");
        }
        if (matrix.getOrientation() != VectorOrientation.COLUMN_MAJOR) {
            throw new IllegalArgumentException("vecMatMul requires matrix to be COLUMN_MAJOR");
        }

        int nCols = matrix.length();
        int m = this.length();

        // Dimension check
        SharedVector col0 = matrix.get(0);
        if (col0.length() != m) {
            throw new IllegalArgumentException(
                "vecMatMul dimension mismatch: row length " + m +
                ", matrix column length " + col0.length()
            );
        }

        this.writeLock();
        SharedVector[] cols = new SharedVector[nCols];
        try {
            // Lock all columns for reading
            for (int j = 0; j < nCols; j++) {
                cols[j] = matrix.get(j);
                cols[j].readLock();
            }

            double[] result = new double[nCols];

            for (int j = 0; j < nCols; j++) {
                SharedVector col = cols[j];
                double sum = 0.0;
                for (int k = 0; k < m; k++) {
                    sum += this.vector[k] * col.vector[k];
                }
                result[j] = sum;
            }

            this.vector = result;

        } finally {
            for (int j = nCols - 1; j >= 0; j--) {
                if (cols[j] != null) cols[j].readUnlock();
            }
            this.writeUnlock();
        }
    }
}
