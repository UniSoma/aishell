(ns aishell.docker.hash
  "SHA-256 hashing for Dockerfile cache invalidation.

   Uses Java MessageDigest directly (no external dependencies).")

(defn compute-hash
  "Compute SHA-256 hash of content, returning first 12 hex characters.

   Arguments:
   - content: String content to hash

   Returns: 12-character hex string (matches sha256sum | cut -c1-12 behavior)

   Same input always produces same output (deterministic).
   Different inputs produce different outputs (collision-resistant)."
  [content]
  (let [md (java.security.MessageDigest/getInstance "SHA-256")
        bytes (.digest md (.getBytes content "UTF-8"))]
    (subs (apply str (map #(format "%02x" (bit-and % 0xff)) bytes)) 0 12)))
