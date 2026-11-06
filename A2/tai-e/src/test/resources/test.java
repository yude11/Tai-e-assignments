public class test  {
    class M {
        int a;
        int b;
    }
    M test1 () {
        M m = new M();
        m.a = 1;
        m.b = 2;
        int x = m.a + m.b;
        return m;
    }
    void testMUL_AND_DIV_ZEEO (int c) {
        int a = 0;
        int b = c/a;
        int d = a*a;
    }

    void test3 () {
        char a = 'a';
        char b = 'b';
    }

    int switch1(int x, int y){
        int a = 0;
        switch(x){
            case 1:
                a-=y;
                break;
            case 2:
                a+=y;
                break;
            case 3:
                a*=y;
                break;
            case 4:
                a/=y;
                break;
            default:
                a = a-1;
        }
        a = a+x;
        return a;
    }

}