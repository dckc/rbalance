Preface: DB Setup
=================

The python standard library includes `sqlite`__::

__ https://www.sqlite.org/index.html

    >>> import sqlite3
    >>> from pprint import pprint, pformat
    >>> from __future__ import print_function
    >>> import urllib2
    >>> from decimal import Decimal as D

We have a few functions to build an audit DB::

    >>> import audit
    >>> db = audit.DB(sqlite3.connect('rev_check.db'))
    >>> db.init_tables()
    [u'genesis', u'snapshot', u'taint']


RHOC Snapshot
=============

Let's load the `snapshot` table with RHOC balances as of ethereum block 9371743:

    >>> with open('src/main/resources/wallets_9371743_withzeros.txt') as w:
    ...     db.load('snapshot', w)

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

Let's label some of these based on `RHOC Distribution`__.

__ https://github.com/rchain/reference/blob/master/finance/rhoc.md

    >>> db.sql('''create table addrbook (addr text, label text)''')
    >>> db.sql('''insert into addrbook (addr, label)
    ...           values ('0x1c73d4ff97b9c8299f55d3b757b70979ee718754', 'Reserve Wallet')''')
    >>> db.sql('''insert into addrbook (addr, label)
    ...           values ('0xd35a2d8c651f3eba4f0a044db961b5b0ccf68a2d', 'Current operation')''')

And from `Ian Feb 13`__:

__ https://discordapp.com/channels/375365542359465989/454113117257859073/677385362443730944

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
    ...     order by bal desc limit 10'''), labels=2)
    label                addr                                                          bal
    Reserve Wallet       0x1c73d4ff97b9c8299f55d3b757b70979ee718754      27466403837716800
                         0x0000000000000000000000000000000000000000      12933642600000000
    Current operation    0xd35a2d8c651f3eba4f0a044db961b5b0ccf68a2d       7793221780308682
    Token Sale Wallet    0x287550958be9d74d7f7152c911ba0b71801153a8       3117693198495265
    KuCoin 2             0x689c56aef474df92d44a1b70850f808488f9769c       2882664288573629
                         0x899b5d52671830f567bf43a14684eb14e1f945fe       2878776400000000
                         0x62917a5bce92bc34bdc6b9254b3cc426d52752f3       2108874000000000
                         0x583c3bceb7b517acaeca84bce7c7266d7290a7aa       1483867335645073
    new REV              0xf15230cba5b211b7cb6a4ae7cfc5a84e9cb6865d       1420881000000000
                         0xbee7cce5b6e2eb556219eef8f3061aa9ff0630e9       1260711500000000


Feb 11 BOD Resolution: Tainted RHOC Amendment
=============================================

    >>> feb11 = 'https://raw.githubusercontent.com/rchain/board/master/2020/02-11/README.md'
    >>> ea = audit.mdtable(urllib2.urlopen(feb11))
    >>> hd = next(ea)
    >>> adj = [(addr, D(bal.replace(',', ''))) for addr, bal in ea]

    >>> taint_lines = ['%s,%d,%d\r\n' % (addr, int(amt * rhoc8), 0) for addr, amt in adj]
    >>> db.load('taint', iter(taint_lines))
    >>> audit.show('{0:<44} {1:>20}', *db.query('select addr, bal from taint'), decimals=8)
    addr                                                          bal
    0x583c3bceb7b517acaeca84bce7c7266d7290a7aa                   0E-8
    0x44d37b82cbbb410a42725d3a51c7f84f3bda12a7                   0E-8
    0xaa9bdb711a1ff305f398777c65ac70be6bf0fa5f                   0E-8
    0xbdcbf01d5a0fbe303a4863b7331f4c3b87db7cc2       1059541.08000000
    0x62917a5bce92bc34bdc6b9254b3cc426d52752f3       1588740.00000000
    0x6e75bc5e6547a67f7cb12709decb2bb28e880c74                   0E-8
    0xdcb05f9afa10f0cf405ed39502d4916cbd96cf74                   0E-8
    0xfd9b2240ff070417fb04b6db3944692334916056                   0E-8
    0x5c13a7f45fee20876e2359698ab55b914c1156db                   0E-8
    0x44948d4bcf984ee51d9e1127f3a0e4bc46bd6910                   0E-8
    0x3198af8d57cba0ba93a7f861432f148b37c3af98                   0E-8
    0xbbd9312f8fb2ae80e99cf661b47d8f3f1f151b5c                   0E-8
    0x689c56aef474df92d44a1b70850f808488f9769c      23816642.89000000


How do snapshot balances compare to taint balances?

    >>> audit.show('{0:<44} {1:>18} {2:>18} {3:>18}', *db.query('''
    ... select t.addr, s.bal bal_rhoc, t.bal bal_taint, t.bal - s.bal as delta
    ... from taint t join snapshot s on s.addr = t.addr
    ... where delta != 0
    ... '''), decimals=8)
    addr                                                   bal_rhoc          bal_taint              delta
    0x583c3bceb7b517acaeca84bce7c7266d7290a7aa    14838673.35645073               0E-8 -14838673.35645073
    0x44d37b82cbbb410a42725d3a51c7f84f3bda12a7     6466991.46410000               0E-8  -6466991.46410000
    0xaa9bdb711a1ff305f398777c65ac70be6bf0fa5f     8927500.00000000               0E-8  -8927500.00000000
    0xbdcbf01d5a0fbe303a4863b7331f4c3b87db7cc2     5122041.08255400   1059541.08000000  -4062500.00255400
    0x62917a5bce92bc34bdc6b9254b3cc426d52752f3    21088740.00000000   1588740.00000000 -19500000.00000000
    0x6e75bc5e6547a67f7cb12709decb2bb28e880c74       10000.00000000               0E-8    -10000.00000000
    0xdcb05f9afa10f0cf405ed39502d4916cbd96cf74     3400500.00000000               0E-8  -3400500.00000000
    0xfd9b2240ff070417fb04b6db3944692334916056      364784.00000000               0E-8   -364784.00000000
    0x5c13a7f45fee20876e2359698ab55b914c1156db      300000.00000000               0E-8   -300000.00000000
    0x44948d4bcf984ee51d9e1127f3a0e4bc46bd6910      135299.00000000               0E-8   -135299.00000000
    0x3198af8d57cba0ba93a7f861432f148b37c3af98     4315002.00000000               0E-8  -4315002.00000000
    0xbbd9312f8fb2ae80e99cf661b47d8f3f1f151b5c        5000.00000000               0E-8     -5000.00000000
    0x689c56aef474df92d44a1b70850f808488f9769c    28826642.88573629  23816642.89000000  -5009999.99573629

Minutes say "For a total recovery of 67,119,258.36 RHOC." Does this check out?

    >>> hd, [[total_recovery]] = db.query('''
    ... select sum(delta) from (
    ... select t.addr, s.bal bal_rhoc, t.bal bal_taint, s.bal - t.bal as delta
    ... from taint t join snapshot s on s.addr = t.addr
    ... )
    ... ''')
    >>> D(total_recovery) / rhoc8
    Decimal('67119258.36')


Genesis REV Wallets Proposal
============================

    >>> genesis_addr = 'https://raw.githubusercontent.com/rchain/rchain/dev/wallets.txt'
    >>> db.load('genesis', urllib2.urlopen(genesis_addr))

How many non-zero REV wallets do we have?  How does the number of REV
wallets compare to the number of RHOC wallets?

    >>> _cols, [[qty_rev]] = db.query('select count(*) qty from genesis where bal != 0')
    >>> qty_rev, qty_rhoc >= qty_rev
    (7329, True)

What are these extra genesis balances???

    >>> audit.show('{0:<55} {1:>20}', *db.query('''
    ...     select g.addr, g.bal from genesis g left join snapshot s on g.addr = s.addr where s.addr is null
    ... '''))

What are the top 10?
    >>> _, top_rev = db.query('select addr, bal from snapshot order by bal desc limit 10')
    >>> top_rhoc == top_rev
    True

How do genesis balances differ from snapshot balances?

    >>> db.sql('''
    ... create view adj as
    ... select s.addr, s.bal bal_rhoc, g.bal bal_rev, g.bal - s.bal as delta
    ... from snapshot s
    ... left join genesis g on g.addr = s.addr
    ... ''');

    >>> audit.show('{0:<44} {1:>20} {2:>20} {3:>20}', decimals=8, *db.query('''
    ...   select coalesce(coalesce(t.label, bk.label) || ' ' || substr(adj.addr, 1, 7), adj.addr) addr
    ...        , adj.bal_rhoc, adj.bal_rev, adj.delta from adj
    ...   left join addrbook bk on bk.addr = adj.addr
    ...   left join (select 'feb 11 taint' label, t.* from taint t) t on t.addr = adj.addr
    ...   where abs(delta) != 0 order by abs(delta) desc, addr
    ... '''))
    addr                                                     bal_rhoc              bal_rev                delta
    Reserve Wallet 0x1c73d                         274664038.37716800                 0E-8  -274664038.37716800
    Current operation 0xd35a2                       77932217.80308682                 0E-8   -77932217.80308682
    Token Sale Wallet 0x28755                       31176931.98495265                 0E-8   -31176931.98495265
    feb 11 taint 0x62917                            21088740.00000000     1588740.00000000   -19500000.00000000
    feb 11 taint 0x583c3                            14838673.35645073                 0E-8   -14838673.35645073
    new REV 0xf1523                                 14208810.00000000                 0E-8   -14208810.00000000
    feb 11 taint 0xaa9bd                             8927500.00000000                 0E-8    -8927500.00000000
    feb 11 taint 0x44d37                             6466991.46410000                 0E-8    -6466991.46410000
    feb 11 taint 0x3198a                             4315002.00000000                 0E-8    -4315002.00000000
    feb 11 taint 0xbdcbf                             5122041.08255400     1059541.08000000    -4062500.00255400
    Research Wallet 0x821aa                          4000000.00000000                 0E-8    -4000000.00000000
    feb 11 taint 0xdcb05                             3400500.00000000                 0E-8    -3400500.00000000
    new REV 0x4c8c0                                   783513.78500000                 0E-8     -783513.78500000
    feb 11 taint 0xfd9b2                              364784.00000000                 0E-8     -364784.00000000
    feb 11 taint 0x5c13a                              300000.00000000                 0E-8     -300000.00000000
    new REV 0xc3a0f                                   203930.75599958                 0E-8     -203930.75599958
    feb 11 taint 0x44948                              135299.00000000                 0E-8     -135299.00000000
    feb 11 taint 0x6e75b                               10000.00000000                 0E-8      -10000.00000000
    feb 11 taint 0xbbd93                                5000.00000000                 0E-8       -5000.00000000

Ian seems to be working with Greg since Feb 11; the KuCoin 2 wallet seems to be un-tainted:

    >>> audit.show('{0:<44} {1:>20} {2:>20} {3:>15} {4:>15}', *db.query('''
    ... select coalesce(bk.label, adj.addr) addr, adj.bal_rhoc, adj.bal_rev, adj.delta, taint.bal taint_bal
    ... from taint join adj on adj.addr = taint.addr
    ... left join addrbook bk on bk.addr = taint.addr
    ... where bal_rev != taint_bal
    ... '''), decimals=8)
    addr                                                     bal_rhoc              bal_rev           delta       taint_bal
    KuCoin 2                                        28826642.88573629    28826642.88573629            0E-8 23816642.89000000
