theory Ex imports Pure
begin

ML \<open>
  val rc = Isabelle_System.bash
    "tmp_dir=\"$ISABELLE_TMP/mirabelle-ex\" \
    \&& tmp_file=\"$tmp_dir/Inner_Product.thy\" \
    \&& mkdir -p \"$tmp_dir\" \
    \&& cp -f \"$ISABELLE_HOME/src/HOL/Analysis/Inner_Product.thy\" \"$tmp_file\" \
    \&& isabelle build -n \"$MIRABELLE_LOGIC\" \
    \&& isabelle mirabelle arith \"$tmp_file\"";
  if rc <> 0 then error ("Mirabelle example failed: " ^ string_of_int rc)
  else ();
\<close> \<comment> \<open>some arbitrary small test case\<close>

end

