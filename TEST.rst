Preface: DB Setup
=================

The python standard library includes `sqlite
<https://www.sqlite.org/index.html>`_ etc.::

    >>> from __future__ import print_function
    >>> from decimal import Decimal as D
    >>> from io import BytesIO
    >>> from pprint import pprint, pformat
    >>> import csv
    >>> import hashlib
    >>> import sqlite3
    >>> import urllib2

We have a few functions to build an audit DB::

    >>> import audit
    >>> db = audit.DB(sqlite3.connect('rev_check.db'))
    >>> db.init_tables()
    [u'genesis', u'snapshot', u'taint']


RHOC Distribution
=================

Let's label some addresses based on `RHOC Distribution
<https://github.com/rchain/reference/blob/master/finance/rhoc.md>`_.

    >>> db.sql('''create table addrbook (addr text, label text)''')
    >>> db.sql('''insert into addrbook (addr, label)
    ...           values ('0x1c73d4ff97b9c8299f55d3b757b70979ee718754', 'Reserve Wallet')''')
    >>> db.sql('''insert into addrbook (addr, label)
    ...           values ('0xd35a2d8c651f3eba4f0a044db961b5b0ccf68a2d', 'Current operation')''')


Scam Addresses
==============

`Important: Scam wallet addresses
<https://blog.rchain.coop/blog/2019/05/03/mitigating-the-barcelona-attack/>`_
May 3, 2019

    >>> scam = [[t.strip() for t in line.split('  ', 1)]
    ...         for line in '''
    ... Ethereum Address	Etherscan Flags
    ... 0xDcb05f9Afa10F0CF405ed39502d4916CBD96cF74	RHOC Scam token 2
    ... 0x381B94bE319fD260C336204a1FE52ab6BD37f31B	RHOC Scam token 3
    ... 0x5b7fe67E46b901272C449E5015550447666247FC	RHOC Scam token 4
    ... 0xDf04E4f99a3D460dB87f58eB331b8B5DbebF2Af0	RHOC Scam token 5
    ... 0x17BB11fBbb92Be1f466A90FeBf108454bB48d520	RHOC Scam token 6
    ... 0x602bda99d7d47b92bd5d4056dbb8219c141a20ad	RHOC Scam token 7
    ... 0xbd0095c6256e29502faf616de563df0a3fe63612	RHOC Scam token 8
    ... 0x4acfa8978e48d6e3c24f57a7b5fb520dbcc5e218	RHOC Scam token 9
    ... 0xff5cfb5500673bb2e04ca6876b30780f3d9a2532	RHOC Scam token 10
    ... 0x3d3b4b2629fcc40a93036d9eb6e6fd34e465b87b	RHOC Scam token 11
    ... 0x14c509ad7352c2b283599d7e0d6311f331aba993	RHOC Scam token 12
    ... 0x1581912a74a68f40b076044ef81312de8a60aab1	RHOC Scam token 13
    ... 0x1581912a74a68f40b076044ef81312de8a60aab1	RHOC Scam token 13
    ... 0x1581912a74a68f40b076044ef81312de8a60aab1	RHOC Scam token 13
    ... 0x1581912a74a68f40b076044ef81312de8a60aab1	RHOC Scam token 13
    ... 0x3198af8d57cba0ba93a7f861432f148b37c3af98	RHOC Scam token 14
    ... 0x3198af8d57cba0ba93a7f861432f148b37c3af98	RHOC Scam token 14
    ... 0x3198af8d57cba0ba93a7f861432f148b37c3af98	RHOC Scam token 14
    ... 0x81e331af8e4b6e2be4038f37ee1c1eca7c172643	RHOC Scam token 15
    ... 0xf01bc68e0cde363b6e535a4fd3f3b96d7ac1e8c1	RHOC Scam token 16
    ... 0xbbd9312f8fb2ae80e99cf661b47d8f3f1f151b5c	RHOC Scam token 17
    ... '''.strip().split('\n')]
    >>> db.load('addrbook', list(set(tuple(r) for r in scam[1:])), cols='addr, label')

    >>> pithia = [[t.strip() for t in line.split('  ', 3)] for line in '''
    ... Date  Sent to  From  Amount
    ... 5/7/19	0x28890444ac3f24009c50cae2a37aacf85d8e9950  Link  4,999,990
    ... 5/7/19	0x1e5ec2bcfcabd56efab32eb0e65546bc4151ec57    4,999,990
    ... 6/11/19	 0x62917a5bce92bc34bdc6b9254b3cc426d52752f3  Link  19,499,000
    ... 6/11/19	 0x62917a5bce92bc34bdc6b9254b3cc426d52752f3  Link  1,000
    ... 6/11/19	 0xbdcbf01d5a0fbe303a4863b7331f4c3b87db7cc2  Link  4,061,500
    ... 6/11/19	 0xbdcbf01d5a0fbe303a4863b7331f4c3b87db7cc2  Link  1,000
    ... 6/11/19	 0xaa9bdb711a1ff305f398777c65ac70be6bf0fa5f  Link  8,936,500
    ... 6/11/19	 0xaa9bdb711a1ff305f398777c65ac70be6bf0fa5f  Link  1,000
    ... 6/12/19	 0x44d37b82cbbb410a42725d3a51c7f84f3bda12a7  Link  6,466,991
    ... 6/12/19	 0x44d37b82cbbb410a42725d3a51c7f84f3bda12a7  Link  1,000
    ... '''.strip().split('\n')]
    >>> db.load('addrbook', [(addr, 'pithia %d %s' % (ix + 1, amt))
    ...                      for (ix, (_dt, addr, _f, amt)) in enumerate(pithia[1:])],
    ...         cols='addr, label')


RHOC Snapshot
=============

Let's load the `snapshot` table with RHOC balances as of ethereum
block 9371743, generated using
`rhoc-balance-reporter <https://github.com/rchain/rhoc-balance-reporter>`_,
version 7a54a41 Aug 15 2019:

    >>> snapaddr = 'https://github.com/rchain-community/RChain-docs/files/4142663/wallets_9371743.txt'
    >>> snaptxt = urllib2.urlopen(snapaddr).read()
    >>> hashlib.sha1(snaptxt).hexdigest()
    '2e089d38d48b18f48fb969f34ae247ff7f4e0ca9'
    >>> db.load('snapshot', csv.reader(BytesIO(snaptxt)))

How many non-zero RHOC account balances do we have?

    >>> _cols, [[qty_rhoc]] = db.query('select count(*) qty from snapshot where bal != 0')
    >>> qty_rhoc
    7336

Recall the RHOC contract has 8 decimals:

    >>> rhoc8 = 10 ** 8

The total of RHOC balances should be 1 billion (10^9) RHOC:

    >>> _, [[total]] = db.query('select sum(bal) from snapshot')
    >>> total / rhoc8 == 10 ** 9
    True

What are the top 10?
    >>> _, top_rhoc = db.query('select addr, bal from snapshot order by bal desc limit 10'); pprint(top_rhoc)
    [(u'0x1c73d4ff97b9c8299f55d3b757b70979ee718754', 27466403837716800),
     (u'0x0000000000000000000000000000000000000000', 12933642600000000),
     (u'0xd35a2d8c651f3eba4f0a044db961b5b0ccf68a2d', 7793221780308682),
     (u'0x287550958be9d74d7f7152c911ba0b71801153a8', 3117693198495265),
     (u'0x689c56aef474df92d44a1b70850f808488f9769c', 2882664288573629),
     (u'0x899b5d52671830f567bf43a14684eb14e1f945fe', 2878776400000000),
     (u'0x62917a5bce92bc34bdc6b9254b3cc426d52752f3', 2108874000000000),
     (u'0x583c3bceb7b517acaeca84bce7c7266d7290a7aa', 1483867335645073),
     (u'0xf15230cba5b211b7cb6a4ae7cfc5a84e9cb6865d', 1420881000000000),
     (u'0xbee7cce5b6e2eb556219eef8f3061aa9ff0630e9', 1260711500000000)]

And from `Ian Feb 13 <https://discordapp.com/channels/375365542359465989/454113117257859073/677385362443730944>`_:

    >>> db.sql('''insert into addrbook (addr, label)
    ...           values ('0x287550958be9d74d7f7152c911ba0b71801153a8', 'Token Sale Wallet')''')

    >>> db.sql('''insert into addrbook (addr, label)
    ...           values ('0x821aa521ecba3f4fdef89cbe1f468636e858d90d', 'Research Wallet')''')

    >>> db.sql('''insert into addrbook (addr, label)
    ...           values ('0xf15230cba5b211b7cb6a4ae7cfc5a84e9cb6865d', 'new REV')''')
    >>> db.sql('''insert into addrbook (addr, label)
    ...           values ('0x4c8c0a6210fbb8678597a22772490ef53c42cfa9', 'new REV')''')
    >>> db.sql('''insert into addrbook (addr, label)
    ...           values ('0xc3a0f3d70cf1e614f734a951e9456e4eed7da2f4', 'new REV')''')


And from etherscan

    >>> db.sql('''insert into addrbook (addr, label)
    ...           values ('0x689c56aef474df92d44a1b70850f808488f9769c', 'KuCoin 2')''')

    >>> audit.show('{0:<20} {1:<44} {2:>20}', *db.query('''
    ...     select a.label, s.addr, s.bal from snapshot s left join addrbook a on a.addr = s.addr
    ...     order by bal desc limit 11'''), labels=2)
    label                addr                                                          bal
    Reserve Wallet       0x1c73d4ff97b9c8299f55d3b757b70979ee718754      27466403837716800
                         0x0000000000000000000000000000000000000000      12933642600000000
    Current operation    0xd35a2d8c651f3eba4f0a044db961b5b0ccf68a2d       7793221780308682
    Token Sale Wallet    0x287550958be9d74d7f7152c911ba0b71801153a8       3117693198495265
    KuCoin 2             0x689c56aef474df92d44a1b70850f808488f9769c       2882664288573629
                         0x899b5d52671830f567bf43a14684eb14e1f945fe       2878776400000000
    pithia 3 19,499,000  0x62917a5bce92bc34bdc6b9254b3cc426d52752f3       2108874000000000
    pithia 4 1,000       0x62917a5bce92bc34bdc6b9254b3cc426d52752f3       2108874000000000
                         0x583c3bceb7b517acaeca84bce7c7266d7290a7aa       1483867335645073
    new REV              0xf15230cba5b211b7cb6a4ae7cfc5a84e9cb6865d       1420881000000000
                         0xbee7cce5b6e2eb556219eef8f3061aa9ff0630e9       1260711500000000


Feb 11 BOD Resolution: Tainted RHOC Amendment
=============================================

cf. `Feb 11 board minutes
<https://raw.githubusercontent.com/rchain/board/master/2020/02-11/README.md>`_.

    >>> feb11 = 'https://raw.githubusercontent.com/rchain/board/master/2020/02-11/README.md'
    >>> ea = audit.mdtable(urllib2.urlopen(feb11))
    >>> hd = next(ea)
    >>> adj = [(addr, D(bal.replace(',', ''))) for addr, bal in ea]

    >>> taint_rows = [(addr, int(amt * rhoc8), 0) for addr, amt in adj]
    >>> db.load('taint', taint_rows)
    >>> audit.show('{0:<44} {1:>20}', *db.query('select addr, bal from taint'), decimals=8)
    addr                                                          bal
    0x583c3bceb7b517acaeca84bce7c7266d7290a7aa                   0E-8
    0xaa9bdb711a1ff305f398777c65ac70be6bf0fa5f                   0E-8
    0xbdcbf01d5a0fbe303a4863b7331f4c3b87db7cc2       1059541.08260000
    0x62917a5bce92bc34bdc6b9254b3cc426d52752f3       1588740.00000000
    0x6e75bc5e6547a67f7cb12709decb2bb28e880c74                   0E-8
    0xdcb05f9afa10f0cf405ed39502d4916cbd96cf74                   0E-8
    0xfd9b2240ff070417fb04b6db3944692334916056                   0E-8
    0x5c13a7f45fee20876e2359698ab55b914c1156db                   0E-8
    0x44948d4bcf984ee51d9e1127f3a0e4bc46bd6910                   0E-8
    0x3198af8d57cba0ba93a7f861432f148b37c3af98                   0E-8
    0xbbd9312f8fb2ae80e99cf661b47d8f3f1f151b5c                   0E-8
    0x689c56aef474df92d44a1b70850f808488f9769c      23826642.88570000


How do snapshot balances compare to taint balances?

    >>> audit.show('{0:<44} {1:>18} {2:>18} {3:>18}', *db.query('''
    ... select coalesce(bk.label, t.addr) addr, s.bal bal_rhoc, t.bal bal_taint, t.bal - s.bal as delta
    ... from taint t join snapshot s on s.addr = t.addr
    ... left join addrbook bk on bk.addr = t.addr
    ... where delta != 0
    ... '''), decimals=8)
    addr                                                   bal_rhoc          bal_taint              delta
    0x583c3bceb7b517acaeca84bce7c7266d7290a7aa    14838673.35645073               0E-8 -14838673.35645073
    pithia 7 8,936,500                             8927500.00000000               0E-8  -8927500.00000000
    pithia 8 1,000                                 8927500.00000000               0E-8  -8927500.00000000
    pithia 5 4,061,500                             5122041.08255400   1059541.08260000  -4062499.99995400
    pithia 6 1,000                                 5122041.08255400   1059541.08260000  -4062499.99995400
    pithia 3 19,499,000                           21088740.00000000   1588740.00000000 -19500000.00000000
    pithia 4 1,000                                21088740.00000000   1588740.00000000 -19500000.00000000
    0x6e75bc5e6547a67f7cb12709decb2bb28e880c74       10000.00000000               0E-8    -10000.00000000
    RHOC Scam token 2                              3400500.00000000               0E-8  -3400500.00000000
    0xfd9b2240ff070417fb04b6db3944692334916056      364784.00000000               0E-8   -364784.00000000
    0x5c13a7f45fee20876e2359698ab55b914c1156db      300000.00000000               0E-8   -300000.00000000
    0x44948d4bcf984ee51d9e1127f3a0e4bc46bd6910      135299.00000000               0E-8   -135299.00000000
    RHOC Scam token 14                             4315002.00000000               0E-8  -4315002.00000000
    RHOC Scam token 17                                5000.00000000               0E-8     -5000.00000000
    KuCoin 2                                      28826642.88573629  23826642.88570000  -5000000.00003629

Minutes say "For a total recovery of 60,869,258 RHOC." As
reported in
`total recovery? issue 9 <https://github.com/rchain/rbalance/issues/9>`_,
I cannot confirm.

    >>> hd, [[total_recovery]] = db.query('''
    ... select sum(delta) from (
    ... select t.addr, s.bal bal_rhoc, t.bal bal_taint, s.bal - t.bal as delta
    ... from taint t join snapshot s on s.addr = t.addr
    ... )
    ... ''')
    >>> D(total_recovery) / rhoc8
    Decimal('60869258')


Genesis REV Wallets Proposal
============================

`wallets.txt <https://raw.githubusercontent.com/rchain/rchain/dev/wallets.txt>`_:

    >>> genesis_addr = 'https://raw.githubusercontent.com/rchain/rchain/dev/wallets.txt'
    >>> db.load('genesis', csv.reader(urllib2.urlopen(genesis_addr)))

How many non-zero REV wallets do we have?  How does the number of REV
wallets compare to the number of RHOC wallets?

    >>> _cols, [[qty_rev]] = db.query('select count(*) qty from genesis where bal != 0')
    >>> qty_rev, qty_rhoc
    (7329, 7336)

What are the top 10?
    >>> _, top_rev = db.query('select addr, bal from snapshot order by bal desc limit 10')
    >>> top_rhoc == top_rev
    True

How does the snapshot supply compare to the genesis supply?  @ian
writes "12,317.034.24 RHOC is missing from wallets.txt because it is
in the bonds file (validators)"

    >>> audit.show('{0:<20} {1:>20} {2:>20} {3:>20}', *db.query('''
    ... select 'supply', tot_rhoc, tot_rev, tot_rev - tot_rhoc delta
    ... from (
    ...   select (select sum(bal) from snapshot) as tot_rhoc
    ...        , (select sum(bal) from genesis) as tot_rev
    ... )'''), decimals=8)
    'supply'                         tot_rhoc              tot_rev                delta
    supply                1000000000.00000000   987682965.75999995   -12317034.24000005

What are the RHOC and REV balances of scam addresses and other known addresses?
    >>> audit.show('{0:<8} {1:<44} {2:>20} {3:>20} {4:>20}', *db.query('''
    ... select substr(bk.addr, 1, 7) addr, bk.label, s.bal bal_rhoc, g.bal bal_rev
    ...      , coalesce(g.bal, 0) - coalesce(s.bal, 0) delta
    ... from addrbook bk
    ... left join snapshot s on s.addr = bk.addr
    ... left join genesis g on g.addr = bk.addr
    ... '''), decimals=8, labels=2)
    addr     label                                                    bal_rhoc              bal_rev                delta
    0x1c73d  Reserve Wallet                                 274664038.37716800                       -274664038.37716800
    0xd35a2  Current operation                               77932217.80308682                        -77932217.80308682
    0xf01bc  RHOC Scam token 16                                                                                     0E-8
    0x381b9  RHOC Scam token 3                                                                                      0E-8
    0x3198a  RHOC Scam token 14                               4315002.00000000                         -4315002.00000000
    0x3d3b4  RHOC Scam token 11                                                                                     0E-8
    0x81e33  RHOC Scam token 15                                                                                     0E-8
    0x5b7fe  RHOC Scam token 4                                                                                      0E-8
    0xdcb05  RHOC Scam token 2                                3400500.00000000                         -3400500.00000000
    0x17bb1  RHOC Scam token 6                                                                                      0E-8
    0x602bd  RHOC Scam token 7                                                                                      0E-8
    0xdf04e  RHOC Scam token 5                                                                                      0E-8
    0xff5cf  RHOC Scam token 10                                                                                     0E-8
    0x15819  RHOC Scam token 13                                                                                     0E-8
    0xbd009  RHOC Scam token 8                                                                                      0E-8
    0xbbd93  RHOC Scam token 17                                  5000.00000000                            -5000.00000000
    0x4acfa  RHOC Scam token 9                                    100.00000000         100.00000000                 0E-8
    0x14c50  RHOC Scam token 12                                                                                     0E-8
    0x28890  pithia 1 4,999,990                                                                                     0E-8
    0x1e5ec  pithia 2 4,999,990                                                                                     0E-8
    0x62917  pithia 3 19,499,000                             21088740.00000000     1588740.00000000   -19500000.00000000
    0x62917  pithia 4 1,000                                  21088740.00000000     1588740.00000000   -19500000.00000000
    0xbdcbf  pithia 5 4,061,500                               5122041.08255400     1059541.08255400    -4062500.00000000
    0xbdcbf  pithia 6 1,000                                   5122041.08255400     1059541.08255400    -4062500.00000000
    0xaa9bd  pithia 7 8,936,500                               8927500.00000000                         -8927500.00000000
    0xaa9bd  pithia 8 1,000                                   8927500.00000000                         -8927500.00000000
    0x44d37  pithia 9 6,466,991                               6466991.46410000     6466991.46410000                 0E-8
    0x44d37  pithia 10 1,000                                  6466991.46410000     6466991.46410000                 0E-8
    0x28755  Token Sale Wallet                               31176931.98495265                        -31176931.98495265
    0x821aa  Research Wallet                                  4000000.00000000                         -4000000.00000000
    0xf1523  new REV                                         14208810.00000000                        -14208810.00000000
    0x4c8c0  new REV                                           783513.78500000                          -783513.78500000
    0xc3a0f  new REV                                           203930.75599958                          -203930.75599958
    0x689c5  KuCoin 2                                        28826642.88573629    23816642.88573620    -5010000.00000009

How do genesis balances differ from snapshot balances?

    >>> db.sql('''
    ... create view adj as
    ... select distinct addr, bal_rhoc, bal_rev, delta from (
    ... select s.addr, s.bal bal_rhoc, g.bal bal_rev, coalesce(g.bal, 0) - s.bal as delta
    ... from snapshot s
    ... left join genesis g on g.addr = s.addr
    ... union all
    ... select g.addr, s.bal bal_rhoc, g.bal bal_rev, g.bal - coalesce(s.bal, 0) as delta
    ... from genesis g
    ... left join snapshot s on s.addr = g.addr
    ... ) where delta != 0
    ... ''');

The total of adjustments is the same ~12M validator bonds amount:

    >>> audit.show('{0:<20} {1:>20}',
    ...            *db.query("select 'total adj', sum(delta) from adj"),
    ...            decimals=8)
    'total adj'                    sum(delta)
    total adj              -12317034.24000005

I'm not sure about these results, as reported in
`struggling to correlate some RHOC accounts to REV accounts issue 10 <https://github.com/rchain/rbalance/issues/10>`_.

    >>> audit.show('{0:<44} {1:>20} {2:>20} {3:>20}', decimals=8, *db.query('''
    ...   select coalesce(coalesce(bk.label, t.label) || ' ' || substr(adj.addr, 1, 7), adj.addr) addr
    ...        , adj.bal_rhoc, adj.bal_rev, adj.delta from adj
    ...   left join addrbook bk on bk.addr = adj.addr
    ...   left join (select 'feb 11 taint' label, t.* from taint t) t on t.addr = adj.addr
    ...   order by abs(delta) desc, addr
    ... '''))
    addr                                                     bal_rhoc              bal_rev                delta
    Reserve Wallet 0x1c73d                         274664038.37716800                       -274664038.37716800
    0x6defba912a6664838eec10417c75d5270932d6c7                          262347004.13716800   262347004.13716800
    0xc9b2b0bbc1558d69fd285d31ee7897d9b808103a                          135767221.20220800   135767221.20220800
    Current operation 0xd35a2                       77932217.80308682                        -77932217.80308682
    Token Sale Wallet 0x28755                       31176931.98495265                        -31176931.98495265
    0xb4c242f379eed1f2a6cdbc1ca7466738f06793a5                           31176931.98495260    31176931.98495260
    pithia 3 19,499,000 0x62917                     21088740.00000000     1588740.00000000   -19500000.00000000
    pithia 4 1,000 0x62917                          21088740.00000000     1588740.00000000   -19500000.00000000
    feb 11 taint 0x583c3                            14838673.35645073                        -14838673.35645073
    new REV 0xf1523                                 14208810.00000000                        -14208810.00000000
    pithia 7 8,936,500 0xaa9bd                       8927500.00000000                         -8927500.00000000
    pithia 8 1,000 0xaa9bd                           8927500.00000000                         -8927500.00000000
    0x42c9625ea0b18a6d427048094a14476cf339cd31                            7104405.00000000     7104405.00000000
    0xeb08e33fdbb693ac2fddd104de0b8a2ac56d6119                            7104405.00000000     7104405.00000000
    KuCoin 2 0x689c5                                28826642.88573629    23816642.88573620    -5010000.00000009
    RHOC Scam token 14 0x3198a                       4315002.00000000                         -4315002.00000000
    pithia 5 4,061,500 0xbdcbf                       5122041.08255400     1059541.08255400    -4062500.00000000
    pithia 6 1,000 0xbdcbf                           5122041.08255400     1059541.08255400    -4062500.00000000
    Research Wallet 0x821aa                          4000000.00000000                         -4000000.00000000
    0x742ab73a29239d0bbdb8548d936f1325a58dd8fb                            3872000.00000000     3872000.00000000
    RHOC Scam token 2 0xdcb05                        3400500.00000000                         -3400500.00000000
    0x198eddaac45d28ec336df3443dfc5f23d16a8a52                            3000000.00000000     3000000.00000000
    0x5333e2064df92d85321ffdab03620f44481442b8                             987444.54099958      987444.54099958
    new REV 0x4c8c0                                   783513.78500000                          -783513.78500000
    feb 11 taint 0xfd9b2                              364784.00000000                          -364784.00000000
    feb 11 taint 0x5c13a                              300000.00000000                          -300000.00000000
    new REV 0xc3a0f                                   203930.75599958                          -203930.75599958
    feb 11 taint 0x44948                              135299.00000000                          -135299.00000000
    0xf6e07f4ae0961a143d164585fc5f134ec438a1ca                             128000.00000000      128000.00000000
    0x95a110f5fcc7cec23e072840f15450817e5e6c90                              48000.00000000       48000.00000000
    0x168296bb09e24a88805cb9c33356536b980d3fc5         13745.04267036                           -13745.04267036
    feb 11 taint 0x6e75b                               10000.00000000                           -10000.00000000
    RHOC Scam token 17 0xbbd93                          5000.00000000                            -5000.00000000

Since the Feb 11 board minutes, 0xbdcbf pithia was adjusted slightly
(to match the "Scam addresses" blog item). And the KuCoin 2 wallet
seems to be partly un-tainted:

    >>> audit.show('{0:<30} {1:>20} {2:>20} {3:>20} {4:>20}', *db.query('''
    ... select coalesce(bk.label, adj.addr) addr, adj.bal_rhoc, adj.bal_rev, adj.delta, taint.bal taint_bal
    ... from taint join adj on adj.addr = taint.addr
    ... left join addrbook bk on bk.addr = taint.addr
    ... where bal_rev != taint_bal
    ... '''), decimals=8)
    addr                                       bal_rhoc              bal_rev                delta            taint_bal
    pithia 5 4,061,500                 5122041.08255400     1059541.08255400    -4062500.00000000     1059541.08260000
    pithia 6 1,000                     5122041.08255400     1059541.08255400    -4062500.00000000     1059541.08260000
    KuCoin 2                          28826642.88573629    23816642.88573620    -5010000.00000009    23826642.88570000
