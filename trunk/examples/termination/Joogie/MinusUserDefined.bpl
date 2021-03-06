type ref;
type realVar;
type classConst;
// type Field x;
// var $HeapVar : <x>[ref, Field x]x;

const unique $null : ref ;
const unique $intArrNull : [int]int ;
const unique $realArrNull : [int]realVar ;
const unique $refArrNull : [int]ref ;

const unique $arrSizeIdx : int;
var $intArrSize : [int]int;
var $realArrSize : [realVar]int;
var $refArrSize : [ref]int;

var $stringSize : [ref]int;

//built-in axioms 
axiom ($arrSizeIdx == -1);

//note: new version doesn't put helpers in the perlude anymore//Prelude finished 



var int$Random$index0 : int;
var java.lang.String$lp$$rp$$Random$args254 : [int]ref;


// procedure is generated by joogie.
function {:inline true} $neref(x : ref, y : ref) returns (__ret : int) {
if (x != y) then 1 else 0
}


// procedure is generated by joogie.
function {:inline true} $realarrtoref($param00 : [int]realVar) returns (__ret : ref);



// procedure is generated by joogie.
function {:inline true} $modreal($param00 : realVar, $param11 : realVar) returns (__ret : realVar);



// procedure is generated by joogie.
function {:inline true} $leref($param00 : ref, $param11 : ref) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $modint($param00 : int, $param11 : int) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $gtref($param00 : ref, $param11 : ref) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $eqrealarray($param00 : [int]realVar, $param11 : [int]realVar) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $addint(x : int, y : int) returns (__ret : int) {
(x + y)
}


// procedure is generated by joogie.
function {:inline true} $subref($param00 : ref, $param11 : ref) returns (__ret : ref);



	 //  @line: 13
// <MinusUserDefined: void main(java.lang.String[])>
procedure void$MinusUserDefined$main$2230($param_0 : [int]ref)
  modifies $stringSize, java.lang.String$lp$$rp$$Random$args254;
 {
var i010 : int;
var i115 : int;
var $z014 : int;
var r08 : [int]ref;
Block30:
	r08 := $param_0;
	 //  @line: 14
	java.lang.String$lp$$rp$$Random$args254 := r08;
	 //  @line: 15
	 call i010 := int$Random$random$2232();
	 //  @line: 16
	 call i115 := int$Random$random$2232();
	 goto Block31;
	 //  @line: 19
Block31:
	 //  @line: 19
	 call $z014 := boolean$MinusUserDefined$gt$2229((i010), (i115));
	 goto Block32;
	 //  @line: 19
Block32:
	 goto Block33, Block35;
	 //  @line: 19
Block33:
	 assume ($eqint(($z014), (0))==1);
	 goto Block34;
	 //  @line: 19
Block35:
	 //  @line: 19
	 assume ($negInt(($eqint(($z014), (0))))==1);
	 //  @line: 21
	i115 := $addint((i115), (1));
	 goto Block31;
	 //  @line: 25
Block34:
	 return;
}


// procedure is generated by joogie.
function {:inline true} $inttoreal($param00 : int) returns (__ret : realVar);



// procedure is generated by joogie.
function {:inline true} $shrint($param00 : int, $param11 : int) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $negReal($param00 : realVar) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $ushrint($param00 : int, $param11 : int) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $refarrtoref($param00 : [int]ref) returns (__ret : ref);



	 //  @line: 5
// <Random: int random()>
procedure int$Random$random$2232() returns (__ret : int)
  modifies int$Random$index0, $stringSize;
 {
var $i018 : int;
var $i222 : int;
var $r119 : [int]ref;
var $i121 : int;
var $i323 : int;
var r020 : ref;
	 //  @line: 6
Block37:
	 //  @line: 6
	$r119 := java.lang.String$lp$$rp$$Random$args254;
	 //  @line: 6
	$i018 := int$Random$index0;
	 assert ($geint(($i018), (0))==1);
	 assert ($ltint(($i018), ($refArrSize[$r119[$arrSizeIdx]]))==1);
	 //  @line: 6
	r020 := $r119[$i018];
	 //  @line: 7
	$i121 := int$Random$index0;
	 //  @line: 7
	$i222 := $addint(($i121), (1));
	 //  @line: 7
	int$Random$index0 := $i222;
	$i323 := $stringSize[r020];
	 //  @line: 8
	__ret := $i323;
	 return;
}


// procedure is generated by joogie.
function {:inline true} $divref($param00 : ref, $param11 : ref) returns (__ret : ref);



// procedure is generated by joogie.
function {:inline true} $mulref($param00 : ref, $param11 : ref) returns (__ret : ref);



// procedure is generated by joogie.
function {:inline true} $neint(x : int, y : int) returns (__ret : int) {
if (x != y) then 1 else 0
}


// procedure is generated by joogie.
function {:inline true} $ltreal($param00 : realVar, $param11 : realVar) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $reftorefarr($param00 : ref) returns (__ret : [int]ref);



// procedure is generated by joogie.
function {:inline true} $gtint(x : int, y : int) returns (__ret : int) {
if (x > y) then 1 else 0
}


// procedure is generated by joogie.
function {:inline true} $reftoint($param00 : ref) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $addref($param00 : ref, $param11 : ref) returns (__ret : ref);



	 //  @line: 2
// <Random: void <clinit>()>
procedure void$Random$$la$clinit$ra$$2233()
  modifies int$Random$index0;
 {
	 //  @line: 3
Block38:
	 //  @line: 3
	int$Random$index0 := 0;
	 return;
}


// procedure is generated by joogie.
function {:inline true} $xorreal($param00 : realVar, $param11 : realVar) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $andref($param00 : ref, $param11 : ref) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $cmpreal(x : realVar, y : realVar) returns (__ret : int) {
if ($ltreal((x), (y)) == 1) then 1 else if ($eqreal((x), (y)) == 1) then 0 else -1
}


// procedure is generated by joogie.
function {:inline true} $addreal($param00 : realVar, $param11 : realVar) returns (__ret : realVar);



// procedure is generated by joogie.
function {:inline true} $gtreal($param00 : realVar, $param11 : realVar) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $eqreal(x : realVar, y : realVar) returns (__ret : int) {
if (x == y) then 1 else 0
}


// procedure is generated by joogie.
function {:inline true} $ltint(x : int, y : int) returns (__ret : int) {
if (x < y) then 1 else 0
}


// procedure is generated by joogie.
function {:inline true} $newvariable($param00 : int) returns (__ret : ref);



// procedure is generated by joogie.
function {:inline true} $divint($param00 : int, $param11 : int) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $geint(x : int, y : int) returns (__ret : int) {
if (x >= y) then 1 else 0
}


// procedure is generated by joogie.
function {:inline true} $mulint($param00 : int, $param11 : int) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $leint(x : int, y : int) returns (__ret : int) {
if (x <= y) then 1 else 0
}


// procedure is generated by joogie.
function {:inline true} $shlref($param00 : ref, $param11 : ref) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $eqrefarray($param00 : [int]ref, $param11 : [int]ref) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $reftointarr($param00 : ref) returns (__ret : [int]int);



// procedure is generated by joogie.
function {:inline true} $ltref($param00 : ref, $param11 : ref) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $mulreal($param00 : realVar, $param11 : realVar) returns (__ret : realVar);



// procedure is generated by joogie.
function {:inline true} $shrref($param00 : ref, $param11 : ref) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $ushrreal($param00 : realVar, $param11 : realVar) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $shrreal($param00 : realVar, $param11 : realVar) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $divreal($param00 : realVar, $param11 : realVar) returns (__ret : realVar);



// procedure is generated by joogie.
function {:inline true} $orint($param00 : int, $param11 : int) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $reftorealarr($param00 : ref) returns (__ret : [int]realVar);



// procedure is generated by joogie.
function {:inline true} $cmpref(x : ref, y : ref) returns (__ret : int) {
if ($ltref((x), (y)) == 1) then 1 else if ($eqref((x), (y)) == 1) then 0 else -1
}


// procedure is generated by joogie.
function {:inline true} $realtoint($param00 : realVar) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $geref($param00 : ref, $param11 : ref) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $orreal($param00 : realVar, $param11 : realVar) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $eqint(x : int, y : int) returns (__ret : int) {
if (x == y) then 1 else 0
}


// procedure is generated by joogie.
function {:inline true} $ushrref($param00 : ref, $param11 : ref) returns (__ret : int);



// <java.lang.String: int length()>
procedure int$java.lang.String$length$59(__this : ref) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $modref($param00 : ref, $param11 : ref) returns (__ret : ref);



// procedure is generated by joogie.
function {:inline true} $eqintarray($param00 : [int]int, $param11 : [int]int) returns (__ret : int);



	 //  @line: 3
// <MinusUserDefined: boolean gt(int,int)>
procedure boolean$MinusUserDefined$gt$2229($param_0 : int, $param_1 : int) returns (__ret : int) {
var i05 : int;
var $z07 : int;
var i16 : int;
Block17:
	i05 := $param_0;
	i16 := $param_1;
	 goto Block18;
	 //  @line: 4
Block18:
	 goto Block21, Block19;
	 //  @line: 4
Block21:
	 //  @line: 4
	 assume ($negInt(($leint((i05), (0))))==1);
	 goto Block22;
	 //  @line: 4
Block19:
	 assume ($leint((i05), (0))==1);
	 goto Block20;
	 //  @line: 9
Block22:
	 goto Block23, Block24;
	 //  @line: 9
Block20:
	 goto Block27, Block25;
	 //  @line: 9
Block23:
	 assume ($leint((i16), (0))==1);
	 goto Block20;
	 //  @line: 9
Block24:
	 //  @line: 9
	 assume ($negInt(($leint((i16), (0))))==1);
	 //  @line: 5
	i05 := $addint((i05), (-1));
	 //  @line: 6
	i16 := $addint((i16), (-1));
	 goto Block18;
	 //  @line: 9
Block27:
	 //  @line: 9
	 assume ($negInt(($leint((i05), (0))))==1);
	 goto Block28;
	 //  @line: 9
Block25:
	 assume ($leint((i05), (0))==1);
	 goto Block26;
	 //  @line: 9
Block28:
	 //  @line: 9
	$z07 := 1;
	 goto Block29;
	 //  @line: 9
Block26:
	 //  @line: 9
	$z07 := 0;
	 goto Block29;
	 //  @line: 9
Block29:
	 //  @line: 9
	__ret := $z07;
	 return;
}


// procedure is generated by joogie.
function {:inline true} $negRef($param00 : ref) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $lereal($param00 : realVar, $param11 : realVar) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $nereal(x : realVar, y : realVar) returns (__ret : int) {
if (x != y) then 1 else 0
}


// <java.lang.Object: void <init>()>
procedure void$java.lang.Object$$la$init$ra$$28(__this : ref);



// procedure is generated by joogie.
function {:inline true} $instanceof($param00 : ref, $param11 : classConst) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $xorref($param00 : ref, $param11 : ref) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $orref($param00 : ref, $param11 : ref) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $intarrtoref($param00 : [int]int) returns (__ret : ref);



// procedure is generated by joogie.
function {:inline true} $subreal($param00 : realVar, $param11 : realVar) returns (__ret : realVar);



// procedure is generated by joogie.
function {:inline true} $shlreal($param00 : realVar, $param11 : realVar) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $negInt(x : int) returns (__ret : int) {
if (x == 0) then 1 else 0
}


// procedure is generated by joogie.
function {:inline true} $gereal($param00 : realVar, $param11 : realVar) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $eqref(x : ref, y : ref) returns (__ret : int) {
if (x == y) then 1 else 0
}


// <Random: void <init>()>
procedure void$Random$$la$init$ra$$2231(__this : ref)  requires ($neref((__this), ($null))==1);
 {
var r017 : ref;
Block36:
	r017 := __this;
	 assert ($neref((r017), ($null))==1);
	 //  @line: 1
	 call void$java.lang.Object$$la$init$ra$$28((r017));
	 return;
}


// procedure is generated by joogie.
function {:inline true} $cmpint(x : int, y : int) returns (__ret : int) {
if (x < y) then 1 else if (x == y) then 0 else -1
}


// procedure is generated by joogie.
function {:inline true} $andint($param00 : int, $param11 : int) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $andreal($param00 : realVar, $param11 : realVar) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $shlint($param00 : int, $param11 : int) returns (__ret : int);



// procedure is generated by joogie.
function {:inline true} $xorint($param00 : int, $param11 : int) returns (__ret : int);



// <MinusUserDefined: void <init>()>
procedure void$MinusUserDefined$$la$init$ra$$2228(__this : ref)  requires ($neref((__this), ($null))==1);
 {
var r01 : ref;
Block16:
	r01 := __this;
	 assert ($neref((r01), ($null))==1);
	 //  @line: 1
	 call void$java.lang.Object$$la$init$ra$$28((r01));
	 return;
}


// procedure is generated by joogie.
function {:inline true} $subint(x : int, y : int) returns (__ret : int) {
(x - y)
}


